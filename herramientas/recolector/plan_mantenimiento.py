"""Extracción determinista por plantilla de marca de planes de mantenimiento y datos de servicio desde PDFs oficiales.

Principios:
- Solo descarga PDFs desde dominios oficiales en lista blanca, respetando robots.txt y rate limit.
- Caché local por hash de URL/contenido (nunca se guardan PDFs en git).
- Detecta rápido PDFs escaneados o en curvas (sin texto seleccionable) y los omite sin inventar datos.
- Todo componente e intervalo extraído pasa cotejo literal automático contra las páginas citadas del PDF.
"""
from __future__ import annotations

import hashlib
import logging
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

import pdfplumber

CARPETA_RECOLECTOR = Path(__file__).resolve().parent
CARPETA_FICHAS_TOOLS = CARPETA_RECOLECTOR.parent / "fichas"
for _p in (str(CARPETA_RECOLECTOR), str(CARPETA_FICHAS_TOOLS)):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import validar  # noqa: E402
from robots_guard import RateLimiter, RobotsGuard, USER_AGENT, es_url_oficial  # noqa: E402

logging.getLogger("pdfminer").setLevel(logging.ERROR)
logger = logging.getLogger("recolector")

MAX_PDF_BYTES = 65 * 1024 * 1024
CARPETA_CACHE_DEFECTO = Path(__file__).resolve().parents[2] / ".cache_pdfs"

_PRIORIDAD_ACCION = {
    "Cambiar": 5,
    "Lubricar": 4,
    "Ajustar": 3,
    "Limpiar": 2,
    "Apretar": 2,
    "Revisar": 1,
}

_BAJAJ_CODIGOS = {
    "R": "Cambiar",
    "L": "Lubricar",
    "A": "Ajustar",
    "CL": "Limpiar",
    "T": "Apretar",
    "C": "Revisar",
}


def _sin_tildes(texto: str) -> str:
    return unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()


def _decodificar_cids_pdf(texto: str) -> str:
    def _reemp(m: re.Match[str]) -> str:
        code = int(m.group(1))
        return chr(code) if 32 <= code <= 255 else m.group(0)

    return re.sub(r"\(cid:(\d{2,3})\)", _reemp, texto)


def _unir_guiones_pdf(texto: str) -> str:
    texto = _decodificar_cids_pdf(texto)
    unido = re.sub(
        r"([A-Za-zÁÉÍÓÚÑáéíóúñ]{2,})-\s*\n\s*([A-Za-zÁÉÍÓÚÑáéíóúñ]{2,})",
        r"\1\2",
        texto,
    )
    unido = re.sub(r"([A-Za-zÁÉÍÓÚÑáéíóúñ])-\s*\n\s*([a-záéíóúñ])", r"\1\2", unido)
    # Algunas celdas/líneas de PDF quedan con guion + espacio en medio de palabra ("combusti- ble", "ma- neta")
    unido = re.sub(r"([a-záéíóúñ]{2,})-\s+([a-záéíóúñ]{2,})", r"\1\2", unido)
    return unido


def normalizar_texto_pdf(texto: str) -> str:
    """Normaliza saltos de línea y guiones de corte de palabra para cotejo literal."""
    unido = _unir_guiones_pdf(texto)
    return re.sub(r"\s+", " ", _sin_tildes(unido)).strip().lower()


def _texto_palabras_pagina(page: Any) -> str:
    """Extrae texto ordenado desde palabras del PDF sin intercalado de líneas."""
    try:
        words = page.extract_words(x_tolerance=2, y_tolerance=2) or []
        if words:
            return " ".join(w["text"] for w in words if w.get("text"))
    except Exception:
        pass
    return page.extract_text() or ""


def _limpiar_componente(raw: str) -> str:
    unido = _unir_guiones_pdf(raw)
    plano = re.sub(r"\s+", " ", unido).strip()
    plano = plano.replace("**", "").replace("*", "").strip("•·- ")
    # Quita marcadores de nota al pie al final (dígitos sueltos de referencia como "Filtro de aire 2")
    plano = re.sub(r"(?:\s+\d{1,2})+$", "", plano).strip()
    plano = re.sub(r"\s+", " ", plano).strip()
    return plano


def _parsear_entero_km(raw: str) -> int | None:
    limpio = raw.strip().replace(".", "").replace(",", "")
    if not limpio.isdigit():
        return None
    val = int(limpio)
    return val if 100 <= val <= 200000 else None


def _extraer_intervalo_texto(texto: str, priorizar_reemplazo: bool = False) -> tuple[int | None, int | None]:
    """Extrae (intervalo_km, intervalo_meses) de frases como 'Cada 15000 km' o 'Cada 2 años'."""
    low = normalizar_texto_pdf(texto)
    km: int | None = None
    meses: int | None = None

    if priorizar_reemplazo:
        m_reemp = re.search(
            r"(?:reemplazar|cambiar)\s+(?:en\s+la\s+primer\s+revision\s+y\s+luego\s+)?cada\s+(\d{1,3}(?:[.,]\d{3})|\d{3,6})\s*km",
            low,
        )
        if m_reemp:
            km = _parsear_entero_km(m_reemp.group(1))

    if km is None:
        m_km = re.search(r"cada\s+(\d{1,3}(?:[.,]\d{3})|\d{3,6})\s*km", low)
        if m_km:
            km = _parsear_entero_km(m_km.group(1))

    m_mes = re.search(r"cada\s+(\d{1,2})\s*mes(?:es)?", low)
    if m_mes:
        val_m = int(m_mes.group(1))
        if 1 <= val_m <= 120:
            meses = val_m
    else:
        m_anio = re.search(r"cada\s+(\d{1,2})\s*an[o]s?", low)
        if m_anio:
            val_a = int(m_anio.group(1))
            if 1 <= val_a <= 10:
                meses = val_a * 12

    if meses is None:
        m_o_mes = re.search(r"\bo\s+(\d{1,2})\s*mes(?:es)?", low)
        if m_o_mes:
            val_m = int(m_o_mes.group(1))
            if 1 <= val_m <= 120:
                meses = val_m
        else:
            m_o_anio = re.search(r"\bo\s+(\d{1,2})\s*an[o]s?", low)
            if m_o_anio:
                val_a = int(m_o_anio.group(1))
                if 1 <= val_a <= 10:
                    meses = val_a * 12

    return km, meses


def _cotejar_componente_en_texto(componente: str, texto_norm: str) -> bool:
    aguja = normalizar_texto_pdf(componente)
    if not aguja or len(aguja) < 2:
        return False
    texto_limpio = texto_norm.replace("*", "")
    if aguja in texto_limpio:
        return True
    aguja_simple = re.sub(r"[,:;()\-/]+", " ", aguja)
    aguja_simple = re.sub(r"\s+", " ", aguja_simple).strip()
    texto_simple = re.sub(r"[,:;()\-/]+", " ", texto_limpio)
    texto_simple = re.sub(r"\s+", " ", texto_simple).strip()
    if aguja_simple in texto_simple:
        return True
    # Algunos PDFs parten una palabra con espacio de kerning (ej. "centrífug o", "Sujeci ones")
    aguja_junta = aguja_simple.replace(" ", "")
    texto_junto = texto_simple.replace(" ", "")
    return len(aguja_junta) >= 5 and aguja_junta in texto_junto


def _variantes_numero_en_texto(n: int) -> list[str]:
    s = str(n)
    variantes = [s]
    if n >= 1000:
        variantes.append(f"{n:,}".replace(",", "."))
        variantes.append(f"{n:,}")
    return variantes


def _cotejar_intervalo_en_texto(
    intervalo_km: int | None,
    intervalo_meses: int | None,
    texto_norm: str,
) -> bool:
    ok_km = True
    if intervalo_km is not None:
        ok_km = any(v in texto_norm for v in _variantes_numero_en_texto(intervalo_km))
        if not ok_km and "x 1000 km" in texto_norm and intervalo_km % 1000 == 0:
            ok_km = True

    ok_mes = True
    if intervalo_meses is not None:
        ok_mes = (
            str(intervalo_meses) in texto_norm
            or (intervalo_meses % 12 == 0 and (f"{intervalo_meses // 12} ano" in texto_norm or "anual" in texto_norm))
        )
    return ok_km and ok_mes


def _paso_periodico(valores: list[int], paso_por_defecto: int | None = None) -> int | None:
    """Deduce el intervalo periódico en km a partir de los hitos marcados."""
    unicos = sorted(set(valores))
    if not unicos:
        return None
    # Excluir hito inicial de asentamiento (< 1500 km) si hay hitos posteriores
    periodicos = [v for v in unicos if v >= 1500]
    if len(periodicos) >= 2:
        diffs = [b - a for a, b in zip(periodicos, periodicos[1:]) if b > a]
        if diffs:
            return min(diffs)
    if len(periodicos) == 1:
        if len(unicos) >= 2 and paso_por_defecto and (periodicos[0] - unicos[0]) <= paso_por_defecto:
            return periodicos[0]
        return periodicos[0]
    # Solo marcó hito inicial (ej. 500-750 o 1000 km): no confundir asentamiento con intervalo recurrente
    return None


def _paso_meses(valores: list[int]) -> int | None:
    unicos = sorted(set(valores))
    if not unicos:
        return None
    # Excluir revisión inicial de 1 mes si hay hitos posteriores periódicos
    periodicos = [m for m in unicos if m >= 2]
    if len(periodicos) >= 2:
        diffs = [b - a for a, b in zip(periodicos, periodicos[1:]) if b > a]
        if diffs:
            return min(diffs)
    if len(periodicos) == 1 and len(unicos) == 1:
        return periodicos[0]
    return None


def _combinar_o_actualizar_tarea(
    tareas_por_clave: dict[str, dict[str, Any]],
    componente: str,
    accion: str,
    intervalo_km: int | None,
    intervalo_meses: int | None,
    pagina: int,
) -> None:
    if not componente or (intervalo_km is None and intervalo_meses is None):
        return
    clave = validar.normalizar(componente)
    nueva = {
        "componente": componente,
        "accion": accion,
        "intervalo_km": intervalo_km,
        "intervalo_meses": intervalo_meses,
        "_pagina": pagina,
        "_prioridad": _PRIORIDAD_ACCION.get(accion.split()[0].rstrip(","), 1),
    }
    existente = tareas_por_clave.get(clave)
    if existente is None:
        tareas_por_clave[clave] = nueva
        return
    if nueva["_prioridad"] > existente["_prioridad"]:
        tareas_por_clave[clave] = nueva
    elif nueva["_prioridad"] == existente["_prioridad"]:
        if existente["intervalo_km"] is None and nueva["intervalo_km"] is not None:
            existente["intervalo_km"] = nueva["intervalo_km"]
        if existente["intervalo_meses"] is None and nueva["intervalo_meses"] is not None:
            existente["intervalo_meses"] = nueva["intervalo_meses"]


# ---------------------------------------------------------------------------
# Descarga segura y caché de PDFs oficiales
# ---------------------------------------------------------------------------

def obtener_pdf_oficial(
    url: str,
    dominios_permitidos: list[str],
    carpeta_cache: Path = CARPETA_CACHE_DEFECTO,
    robots_guard: RobotsGuard | None = None,
    rate_limiter: RateLimiter | None = None,
    timeout_seg: int = 45,
) -> tuple[Path | None, str | None, str | None]:
    """Descarga (o lee de caché) un PDF oficial. Devuelve (ruta_pdf, sha256_hex, motivo_error)."""
    if not es_url_oficial(url, dominios_permitidos):
        return None, None, f"URL fuera de lista blanca o sin HTTPS: {url}"
    guard = robots_guard or RobotsGuard()
    if not guard.puede_consultar(url):
        return None, None, f"robots.txt prohíbe consultar: {url}"

    carpeta_cache.mkdir(parents=True, exist_ok=True)
    url_hash = hashlib.sha256(url.encode("utf-8")).hexdigest()[:24]
    ruta_pdf = carpeta_cache / f"{url_hash}.pdf"

    if not (ruta_pdf.is_file() and ruta_pdf.stat().st_size > 1000):
        limiter = rate_limiter or RateLimiter()
        ultimo_error: Exception | None = None
        for intento in range(4):
            limiter.esperar(url)
            req = urllib.request.Request(
                url,
                headers={"User-Agent": USER_AGENT, "Accept": "application/pdf,*/*"},
            )
            try:
                with urllib.request.urlopen(req, timeout=timeout_seg) as resp:
                    final_url = resp.geturl()
                    if not es_url_oficial(final_url, dominios_permitidos):
                        return None, None, f"Redirección fuera de lista blanca: {final_url}"
                    chunks: list[bytes] = []
                    total = 0
                    while True:
                        chunk = resp.read(256 * 1024)
                        if not chunk:
                            break
                        total += len(chunk)
                        if total > MAX_PDF_BYTES:
                            return None, None, f"PDF excede el límite de {MAX_PDF_BYTES // (1024 * 1024)} MB"
                        chunks.append(chunk)
                    contenido = b"".join(chunks)
                    if not contenido.lstrip().startswith(b"%PDF"):
                        return None, None, "La respuesta no es un PDF válido"
                    ruta_pdf.write_bytes(contenido)
                ultimo_error = None
                break
            except urllib.error.HTTPError as e:
                ultimo_error = e
                if e.code in (429, 503) and intento < 3:
                    espera = 6.0 * (2**intento)
                    logger.info("HTTP %d descargando PDF %s; esperando %.0fs", e.code, url, espera)
                    time.sleep(espera)
                    continue
                return None, None, f"Error descargando PDF: {e}"
            except Exception as e:
                return None, None, f"Error descargando PDF: {e}"
        if ultimo_error is not None:
            return None, None, f"Error descargando PDF: {ultimo_error}"

    data = ruta_pdf.read_bytes()
    sha256 = hashlib.sha256(data).hexdigest()
    return ruta_pdf, sha256, None


def pdf_tiene_texto_extraible(pdf: pdfplumber.PDF, muestras: int = 8) -> bool:
    """Detecta rápidamente si el PDF tiene texto seleccionable (vs escaneado o en curvas)."""
    total = len(pdf.pages)
    if total == 0:
        return False
    paso = max(1, total // muestras)
    indices = list(range(0, total, paso))[:muestras]
    if total - 1 not in indices:
        indices.append(total - 1)
    chars = 0
    for idx in indices:
        txt = pdf.pages[idx].extract_text() or ""
        chars += len(txt.strip())
        if chars >= 200:
            return True
    return False


# ---------------------------------------------------------------------------
# Plantilla Yamaha
# ---------------------------------------------------------------------------

def _yamaha_accion_desde_operacion(texto_op: str, texto_celda: str = "") -> str:
    low = normalizar_texto_pdf(f"{texto_op} {texto_celda}")
    condicional = any(
        k in low
        for k in (
            "si es necesario",
            "segun sea necesario",
            "cuando sea necesario",
            "en caso de que sea necesario",
            "limite de desgaste",
            "ajustar o cambiar el cable",
        )
    )
    tiene_cambiar = any(k in low for k in ("cambiar", "cambie", "reemplazar", "sustituir"))
    tiene_lubricar = any(k in low for k in ("lubricar", "lubrique", "engrasar"))
    tiene_ajustar = any(k in low for k in ("ajustar", "ajuste", "reajuste", "regular"))
    tiene_limpiar = any(k in low for k in ("limpiar", "limpie"))

    if tiene_cambiar and not condicional and "indicador" not in low:
        if "aceite" in low and "motor" not in low:
            return "Cambiar aceite"
        if "refrigerante" in low:
            return "Cambiar refrigerante"
        return "Cambiar"
    if tiene_lubricar:
        return "Ajustar y lubricar" if tiene_ajustar else "Lubricar"
    if tiene_limpiar and tiene_ajustar:
        return "Limpiar y ajustar"
    if tiene_limpiar:
        return "Limpiar"
    if tiene_ajustar:
        return "Revisar y ajustar"
    return "Revisar"


def _es_celda_marcada_yamaha(val_c: str, accion: str) -> bool:
    limpia = val_c.strip()
    if limpia in ("√", "✔", "✓", "X", "x"):
        return True
    norm = re.sub(r"\s+", "", normalizar_texto_pdf(limpia))
    if accion.startswith("Cambiar") and norm.startswith(("cambiar", "cambie")):
        return True
    if "engrasar" in norm and len(norm) <= 12:
        return True
    return False


def _yamaha_columnas_tabla(
    tabla: list[list[str | None]],
) -> tuple[int, int, int, dict[int, int], dict[int, int], int | None, int | None, int | None, int | None] | None:
    """Localiza columnas (idx_elem, idx_op, fila_datos, col->km, col->mes, col_anual, col_cada, cada_km, cada_mes)."""
    idx_elem: int | None = None
    idx_op: int | None = None
    col_km: dict[int, int] = {}
    col_mes: dict[int, int] = {}
    col_anual: int | None = None
    col_cada: int | None = None
    cada_km: int | None = None
    cada_mes: int | None = None
    ultima_fila_header = -1

    escala_x1000 = False
    fila_x1000_km: int | None = None

    for r_idx, row in enumerate(tabla[:6]):
        row_norm = [normalizar_texto_pdf(c or "") for c in row]
        unida = " ".join(row_norm)
        if "x 1000 km" in unida:
            escala_x1000 = True
        for c_idx, txt in enumerate(row_norm):
            if not txt:
                continue
            if txt in ("elemento", "elementos") or txt.startswith("elemento"):
                idx_elem = c_idx
                ultima_fila_header = max(ultima_fila_header, r_idx)
            elif (
                ("comprobacion" in txt and "operacion" in txt)
                or txt.startswith("operacion")
                or txt.startswith("trabajo de comprobacion")
                or txt.startswith("trabajo de revision")
            ):
                idx_op = c_idx
                ultima_fila_header = max(ultima_fila_header, r_idx)
            elif "anual" in txt and col_anual is None:
                col_anual = c_idx
                ultima_fila_header = max(ultima_fila_header, r_idx)
            elif "cada" in txt and col_cada is None:
                m_km_c = re.search(r"(\d{1,3}(?:[.,]\d{3})|\d{3,6})\s*km", txt)
                m_mes_c = re.search(r"(\d{1,2})\s*mes", txt)
                if m_km_c or m_mes_c:
                    col_cada = c_idx
                    cada_km = _parsear_entero_km(m_km_c.group(1)) if m_km_c else None
                    cada_mes = int(m_mes_c.group(1)) if m_mes_c else None
                    ultima_fila_header = max(ultima_fila_header, r_idx)

            m_km = re.search(r"(\d{1,3}(?:[.,]\d{3})|\d{3,6})\s*km", txt)
            if m_km and "x 1000 km" not in txt and "cada" not in txt:
                km_val = _parsear_entero_km(m_km.group(1))
                if km_val is not None:
                    col_km[c_idx] = km_val
                    m_mes_cel = re.search(r"(\d{1,2})\s*mes", txt)
                    if m_mes_cel:
                        col_mes[c_idx] = int(m_mes_cel.group(1))
                    ultima_fila_header = max(ultima_fila_header, r_idx)

        if escala_x1000 and fila_x1000_km is None:
            nums = [int(c) for c in row_norm if re.fullmatch(r"\d{1,2}", c or "")]
            if len(nums) >= 4:
                fila_x1000_km = r_idx
                for c_idx, c in enumerate(row_norm):
                    if re.fullmatch(r"\d{1,2}", c or ""):
                        col_km[c_idx] = int(c) * 1000
                ultima_fila_header = max(ultima_fila_header, r_idx)

    if escala_x1000 and fila_x1000_km is not None:
        for r_idx, row in enumerate(tabla[:6]):
            if r_idx <= fila_x1000_km:
                continue
            row_norm = [normalizar_texto_pdf(c or "") for c in row]
            if any("." in c for c in row_norm):
                continue
            nums = [int(c) for c in row_norm if re.fullmatch(r"\d{1,2}", c or "")]
            if len(nums) >= 4 and nums[0] == 1 and nums[-1] >= 24:
                col_mes = {
                    c_idx: int(c)
                    for c_idx, c in enumerate(row_norm)
                    if re.fullmatch(r"\d{1,2}", c or "")
                }
                ultima_fila_header = max(ultima_fila_header, r_idx)

    if idx_elem is None or idx_op is None or not col_km:
        return None
    return idx_elem, idx_op, ultima_fila_header + 1, col_km, col_mes, col_anual, col_cada, cada_km, cada_mes


def extraer_plan_yamaha_desde_paginas(
    paginas: list[tuple[int, str, list[list[list[str | None]]]]],
) -> tuple[int | None, list[dict[str, Any]]]:
    """Extrae (pagina_inicio, tareas) desde una lista de (num_pagina, texto, tablas) de un manual Yamaha."""
    tareas_por_clave: dict[str, dict[str, Any]] = {}
    pagina_inicio: int | None = None

    for pno, texto, tablas in paginas:
        low = normalizar_texto_pdf(texto)
        if not tablas or "tabla de contenidos" in low or "indice" in low[:80]:
            continue
        if "elemento" not in low or ("cuentakil" not in low and "odometro" not in low and "mantenimiento" not in low):
            continue

        for tabla in tablas:
            if len(tabla) < 3:
                continue
            cols_info = _yamaha_columnas_tabla(tabla)
            if cols_info is None:
                continue
            idx_elem, idx_op, fila_ini, col_km, col_mes, col_anual, col_cada, cada_km, cada_mes = cols_info
            if pagina_inicio is None:
                pagina_inicio = pno

            ultimo_elemento = ""
            for row in tabla[fila_ini:]:
                if len(row) <= max(idx_elem, idx_op):
                    continue
                elem_raw = (row[idx_elem] or "").strip()
                if elem_raw:
                    ultimo_elemento = _limpiar_componente(elem_raw)
                componente = ultimo_elemento
                if not componente or len(componente) < 2:
                    continue

                op_raw = (row[idx_op] or "").strip()
                celdas_hitos = [str(row[c] or "").strip() for c in range(max(idx_elem, idx_op) + 1, len(row))]
                texto_hitos = " ".join(c for c in celdas_hitos if c)
                if not op_raw and not texto_hitos:
                    continue

                accion = _yamaha_accion_desde_operacion(op_raw, texto_hitos)
                km_explicit, meses_explicit = _extraer_intervalo_texto(f"{op_raw} {texto_hitos}")

                km_intervalo = km_explicit
                meses_intervalo = meses_explicit

                if km_intervalo is None:
                    kms_marcados: list[int] = []
                    meses_marcados: list[int] = []
                    for c_idx, km_val in col_km.items():
                        if c_idx < len(row):
                            val_c = (row[c_idx] or "").strip()
                            if _es_celda_marcada_yamaha(val_c, accion):
                                kms_marcados.append(km_val)
                                if c_idx in col_mes:
                                    meses_marcados.append(col_mes[c_idx])
                    km_intervalo = _paso_periodico(kms_marcados, cada_km)
                    if meses_intervalo is None:
                        meses_intervalo = _paso_meses(meses_marcados)

                if col_cada is not None and col_cada < len(row):
                    if _es_celda_marcada_yamaha((row[col_cada] or "").strip(), accion):
                        if km_intervalo is None:
                            km_intervalo = cada_km
                        if meses_intervalo is None:
                            meses_intervalo = cada_mes

                if meses_intervalo is None and col_anual is not None and col_anual < len(row):
                    if (row[col_anual] or "").strip() in ("√", "✔", "✓", "X", "x"):
                        meses_intervalo = 12

                if km_intervalo is None and meses_intervalo is None:
                    continue

                # Cotejo literal obligatorio contra el texto de la página actual
                if not _cotejar_componente_en_texto(componente, low):
                    continue
                if not _cotejar_intervalo_en_texto(km_intervalo, meses_intervalo, low):
                    continue

                _combinar_o_actualizar_tarea(
                    tareas_por_clave, componente, accion, km_intervalo, meses_intervalo, pno
                )

    tareas = [
        {
            "componente": t["componente"],
            "accion": t["accion"],
            "intervalo_km": t["intervalo_km"],
            "intervalo_meses": t["intervalo_meses"],
        }
        for t in tareas_por_clave.values()
    ]
    return pagina_inicio, tareas


# ---------------------------------------------------------------------------
# Plantilla Bajaj
# ---------------------------------------------------------------------------

def _bajaj_parsear_hito_km(raw: str) -> int | None:
    s = normalizar_texto_pdf(raw)
    nums = [
        _parsear_entero_km(m)
        for m in re.findall(r"\d{1,3}(?:[.,]\d{3})|\d{3,6}", s)
    ]
    nums = [n for n in nums if n is not None]
    if not nums:
        return None
    if len(nums) >= 2:
        # Ej: "4500-5000" -> 5000, "5.000-5.500" -> 5000
        if nums[1] % 1000 == 0:
            return nums[1]
        return nums[0]
    return nums[0]


def _bajaj_parsear_meses(raw: str) -> int | None:
    s = normalizar_texto_pdf(raw)
    m = re.search(r"(\d{1,2})\s*mes", s)
    return int(m.group(1)) if m else None


def _bajaj_tokens_accion(celda: str) -> set[str]:
    limpia = (celda or "").strip().upper()
    if not limpia:
        return set()
    # Normalizar "C L" separado por espacio en "CL" y letras duplicadas por superposición de PDF ("RR" -> "R")
    limpia = re.sub(r"\bC\s+L\b", "CL", limpia)
    tokens: set[str] = set()
    for tok in re.split(r"[,;/\s.]+", limpia):
        if not tok:
            continue
        if tok in _BAJAJ_CODIGOS:
            tokens.add(tok)
        elif len(tok) >= 2 and len(set(tok)) == 1 and tok[0] in _BAJAJ_CODIGOS:
            tokens.add(tok[0])
    return tokens


def _bajaj_accion_legible(codigos: set[str]) -> str:
    if codigos == {"R"}:
        return "Cambiar"
    orden = ["C", "CL", "L", "A", "T", "R"]
    presentes = [_BAJAJ_CODIGOS[c] for c in orden if c in codigos]
    if not presentes:
        return "Revisar"
    if len(presentes) == 1:
        return presentes[0]
    if "Cambiar" in presentes and len(presentes) == 2 and "Revisar" in presentes:
        return "Revisar y cambiar si es necesario"
    return ", ".join(presentes[:-1]) + " y " + presentes[-1]


def _bajaj_columnas_tabla(
    tabla: list[list[str | None]],
) -> tuple[int, int, int, int, dict[int, int], dict[int, int], int | None, int | None] | None:
    """Devuelve (idx_op, idx_notas, fila_ini, col_cada, col_km, col_mes, paso_cada_km, paso_cada_mes)."""
    idx_op: int | None = None
    idx_notas: int | None = None
    col_km: dict[int, int] = {}
    col_mes: dict[int, int] = {}
    col_cada: int = -1
    paso_cada_km: int | None = None
    paso_cada_mes: int | None = None
    ultima_fila_header = -1

    for r_idx, row in enumerate(tabla[:4]):
        # Si la primera celda ya es un número de ítem (1, 2, 3...), es fila de datos, no encabezado.
        primera = (row[0] or "").strip() if row else ""
        if re.fullmatch(r"\d{1,2}", primera):
            break
        row_norm = [normalizar_texto_pdf(c or "") for c in row]
        for c_idx, txt in enumerate(row_norm):
            if not txt:
                continue
            if txt == "operacion" or txt.startswith("operacion"):
                idx_op = c_idx
                ultima_fila_header = max(ultima_fila_header, r_idx)
            elif txt.startswith("item") and "operacion" in txt and idx_op is None:
                if c_idx + 1 < len(row) and not (row[c_idx + 1] or "").strip():
                    idx_op = c_idx + 1
                else:
                    idx_op = c_idx
                ultima_fila_header = max(ultima_fila_header, r_idx)
            elif txt in ("notas", "anotaciones"):
                idx_notas = c_idx
                ultima_fila_header = max(ultima_fila_header, r_idx)
            elif ("cada" in txt or "en adelante" in txt) and c_idx != idx_notas:
                col_cada = c_idx
                km_c = _bajaj_parsear_hito_km(txt)
                mes_c = _bajaj_parsear_meses(txt)
                if km_c is not None and "mes" not in txt:
                    paso_cada_km = km_c
                if mes_c is not None:
                    paso_cada_mes = mes_c
                ultima_fila_header = max(ultima_fila_header, r_idx)

        if any(t in ("km", "kms", "k/m") for t in row_norm):
            for c_idx, txt in enumerate(row_norm):
                if c_idx == col_cada or txt in ("km", "kms", "k/m"):
                    continue
                km_val = _bajaj_parsear_hito_km(txt)
                if km_val is not None:
                    col_km[c_idx] = km_val
            ultima_fila_header = max(ultima_fila_header, r_idx)

        if any("tiempo" in t for t in row_norm) or sum(1 for t in row_norm if "mes" in t) >= 3:
            for c_idx, txt in enumerate(row_norm):
                if c_idx == col_cada:
                    continue
                m_val = _bajaj_parsear_meses(txt)
                if m_val is not None:
                    col_mes[c_idx] = m_val
            ultima_fila_header = max(ultima_fila_header, r_idx)

    if idx_op is None or not col_km:
        return None
    if idx_notas is None:
        idx_notas = len(tabla[0]) - 1
    return idx_op, idx_notas, ultima_fila_header + 1, col_cada, col_km, col_mes, paso_cada_km, paso_cada_mes


def _reconstruir_filas_bajaj_sin_bordes(page: Any, tabla_raw: Any) -> list[list[str | None]]:
    """Reconstruye filas cuando una página Bajaj fusiona todos los ítems en una sola celda por falta de líneas."""
    extraida = tabla_raw.extract()
    if len(extraida) != 3:
        return extraida
    fila_merged = extraida[2]
    if not fila_merged or "\n" not in (fila_merged[0] or ""):
        return extraida
    if not re.search(r"(?:^|\n)\d{1,2}\n\d{1,2}", fila_merged[0] or ""):
        return extraida

    try:
        rows_obj = tabla_raw.rows
        if len(rows_obj) < 3:
            return extraida
        col_boxes = [c for c in rows_obj[2].cells if c is not None]
        if len(col_boxes) < 5:
            return extraida
        x_ranges: list[tuple[float, float] | None] = [
            (c[0], c[2]) if c is not None else None for c in rows_obj[2].cells
        ]
        for idx_c in range(len(x_ranges)):
            if x_ranges[idx_c] is None:
                for r_alt in (rows_obj[0], rows_obj[1]):
                    if idx_c < len(r_alt.cells) and r_alt.cells[idx_c] is not None:
                        rc = r_alt.cells[idx_c]
                        x_ranges[idx_c] = (rc[0], rc[2])
                        break

        y_inicio = rows_obj[2].bbox[1]
        words = [w for w in (page.extract_words(x_tolerance=2, y_tolerance=2) or []) if w["top"] >= y_inicio - 2]
        x0_item = x_ranges[0]
        if x0_item is None:
            return extraida

        items_words = sorted(
            [
                w
                for w in words
                if x0_item[0] - 2 <= (w["x0"] + w["x1"]) / 2 <= x0_item[1] + 2
                and re.fullmatch(r"\d{1,2}", w["text"].strip())
            ],
            key=lambda w: w["top"],
        )
        if len(items_words) < 2:
            return extraida

        # Fila de encabezado de Tiempo / Meses (arriba del primer ítem)
        y_primer_item = items_words[0]["top"] - 2
        header_row = [""] * len(x_ranges)
        for c_i, xr in enumerate(x_ranges):
            if xr is None:
                continue
            cw = [
                w["text"]
                for w in words
                if w["top"] < y_primer_item and xr[0] - 2 <= (w["x0"] + w["x1"]) / 2 <= xr[1] + 2
            ]
            header_row[c_i] = " ".join(cw)

        resultado = [extraida[0], extraida[1], header_row]
        for i_w, iw in enumerate(items_words):
            y_top = iw["top"] - 2
            y_bot = items_words[i_w + 1]["top"] - 2 if i_w + 1 < len(items_words) else rows_obj[2].bbox[3] + 2
            fila_nueva: list[str | None] = []
            for xr in x_ranges:
                if xr is None:
                    fila_nueva.append(None)
                    continue
                cw = sorted(
                    [
                        w
                        for w in words
                        if y_top <= (w["top"] + w["bottom"]) / 2 < y_bot
                        and xr[0] - 2 <= (w["x0"] + w["x1"]) / 2 <= xr[1] + 2
                    ],
                    key=lambda w: (round(w["top"], 1), w["x0"]),
                )
                fila_nueva.append(" ".join(w["text"] for w in cw))
            resultado.append(fila_nueva)
        return resultado
    except Exception:
        return extraida


def extraer_plan_bajaj_desde_paginas(
    paginas: list[tuple[int, str, list[list[list[str | None]]]]],
) -> tuple[int | None, list[dict[str, Any]]]:
    """Extrae (pagina_inicio, tareas) desde un manual Bajaj."""
    tareas_por_clave: dict[str, dict[str, Any]] = {}
    pagina_inicio: int | None = None

    for pno, texto, tablas in paginas:
        low = normalizar_texto_pdf(texto)
        if not tablas or ("frecuencia recomendada" not in low and "mantenimiento periodico" not in low):
            continue
        if "operacion" not in low or ("item" not in low and "sr. no" not in low and "rev" not in low):
            continue

        for tabla in tablas:
            if len(tabla) < 4:
                continue
            cols_info = _bajaj_columnas_tabla(tabla)
            if cols_info is None:
                continue
            idx_op, idx_notas, fila_ini, col_cada, col_km, col_mes, paso_cada_km, paso_cada_mes = cols_info
            if pagina_inicio is None:
                pagina_inicio = pno

            for row in tabla[fila_ini:]:
                if len(row) <= idx_op:
                    continue
                op_raw = (row[idx_op] or "").strip()
                componente = _limpiar_componente(op_raw)
                if not componente or len(componente) < 3:
                    continue
                notas_raw = (row[idx_notas] or "").strip() if idx_notas < len(row) else ""
                celdas_intermedias = " ".join(
                    (row[c] or "").strip()
                    for c in range(idx_op + 1, idx_notas)
                    if c < len(row) and (row[c] or "").strip()
                )

                # Revisar si hay instrucción explícita de intervalo en notas o celdas combinadas
                km_nota, mes_nota = _extraer_intervalo_texto(
                    f"{celdas_intermedias} {notas_raw}",
                    priorizar_reemplazo=True,
                )

                cols_con_r: list[int] = []
                cols_con_algo: list[int] = []
                todos_codigos: set[str] = set()
                for c_idx, km_val in sorted(col_km.items()):
                    if c_idx >= len(row):
                        continue
                    cods = _bajaj_tokens_accion(row[c_idx] or "")
                    if cods:
                        cols_con_algo.append(km_val)
                        todos_codigos.update(cods)
                        if cods == {"R"}:
                            cols_con_r.append(km_val)

                tiene_cada_r = False
                tiene_cada_algo = False
                if 0 <= col_cada < len(row):
                    cods_cada = _bajaj_tokens_accion(row[col_cada] or "")
                    if cods_cada:
                        tiene_cada_algo = True
                        todos_codigos.update(cods_cada)
                        if cods_cada == {"R"}:
                            tiene_cada_r = True

                low_notas = normalizar_texto_pdf(f"{celdas_intermedias} {notas_raw}")
                if cols_con_r:
                    accion = "Cambiar"
                    km_intervalo = _paso_periodico(cols_con_r, paso_cada_km)
                    if km_intervalo is None and len(cols_con_r) >= 1 and tiene_cada_r and paso_cada_km:
                        km_intervalo = paso_cada_km
                    if km_intervalo is None and km_nota is not None:
                        km_intervalo = km_nota
                    mes_intervalo = mes_nota or (paso_cada_mes if tiene_cada_r and km_intervalo == paso_cada_km else None)
                elif km_nota is not None and any(k in low_notas for k in ("reemplazar cada", "cambiar cada")):
                    accion = "Cambiar"
                    km_intervalo = km_nota
                    mes_intervalo = mes_nota
                elif km_nota is not None and "lubric" in low_notas and not cols_con_algo:
                    accion = "Lubricar"
                    km_intervalo = km_nota
                    mes_intervalo = mes_nota
                elif cols_con_algo:
                    accion = _bajaj_accion_legible(todos_codigos)
                    km_intervalo = _paso_periodico(cols_con_algo, paso_cada_km)
                    if km_intervalo is None and tiene_cada_algo and paso_cada_km:
                        km_intervalo = paso_cada_km
                    mes_intervalo = mes_nota or (paso_cada_mes if tiene_cada_algo else None)
                    if mes_intervalo is None and len(col_mes) >= 2:
                        mes_marcados = [
                            col_mes[c_idx]
                            for c_idx in col_km
                            if c_idx in col_mes and c_idx < len(row) and _bajaj_tokens_accion(row[c_idx] or "")
                        ]
                        mes_intervalo = _paso_meses(mes_marcados)
                elif km_nota is not None or mes_nota is not None:
                    accion = "Lubricar" if "lubric" in low_notas else ("Cambiar" if "reemplaz" in low_notas else "Revisar")
                    km_intervalo = km_nota
                    mes_intervalo = mes_nota
                else:
                    continue

                if km_intervalo is None and mes_intervalo is None:
                    continue

                if not _cotejar_componente_en_texto(componente, low):
                    continue
                if not _cotejar_intervalo_en_texto(km_intervalo, mes_intervalo, low):
                    continue

                _combinar_o_actualizar_tarea(
                    tareas_por_clave, componente, accion, km_intervalo, mes_intervalo, pno
                )

    tareas = [
        {
            "componente": t["componente"],
            "accion": t["accion"],
            "intervalo_km": t["intervalo_km"],
            "intervalo_meses": t["intervalo_meses"],
        }
        for t in tareas_por_clave.values()
    ]
    return pagina_inicio, tareas


# ---------------------------------------------------------------------------
# Extracción opcional de datos de servicio desde el mismo PDF (con cotejo literal)
# ---------------------------------------------------------------------------

def _formatear_rango_mm(a_str: str, b_str: str) -> str:
    def _num(s: str) -> str:
        val = float(s.replace(",", "."))
        return str(int(val)) if abs(val - round(val)) < 1e-6 else f"{val:.1f}".rstrip("0").rstrip(".")

    return f"{_num(a_str)}-{_num(b_str)} mm"


def extraer_datos_servicio_pdf(
    paginas_texto: list[tuple[int, str]],
) -> dict[str, dict[str, Any]]:
    """Extrae capacidad/viscosidad de aceite, presión de llantas, holgura de cadena y líquido de frenos."""
    datos: dict[str, dict[str, Any]] = {}

    for pno, texto_raw in paginas_texto:
        if not texto_raw:
            continue
        texto_norm = normalizar_texto_pdf(texto_raw)
        lineas = [re.sub(r"\s+", " ", l).strip() for l in _unir_guiones_pdf(texto_raw).splitlines() if l.strip()]

        # 1. Viscosidad de aceite (ej. 10W-40, SAE 20W50, SAE 10 W 50)
        if "viscosidad_aceite" not in datos and ("viscosidad" in texto_norm or "aceite" in texto_norm):
            m_visc = re.search(
                r"(?:viscosidad(?:\s+sae)?|sae)\s*[:\-]?\s*(\d{1,2}\s*W\s*[-–]?\s*\d{2})",
                texto_raw,
                re.IGNORECASE,
            )
            if not m_visc:
                for i, l in enumerate(lineas[:-1]):
                    if "viscosidad" in l.lower():
                        m_sub = re.search(r"\b(\d{1,2}\s*W\s*[-–]?\s*\d{2})\b", lineas[i + 1], re.IGNORECASE)
                        if m_sub:
                            m_visc = m_sub
                            break
            if m_visc:
                raw_v = re.sub(r"\s+", "", m_visc.group(1).upper())
                if "-" not in raw_v:
                    raw_v = raw_v.replace("W", "W-")
                if re.search(r"\b" + re.escape(m_visc.group(1).strip()), texto_raw, re.IGNORECASE):
                    datos["viscosidad_aceite"] = {"valor": f"SAE {raw_v}", "pagina": pno}

        # 2. Capacidad de aceite
        if "capacidad_aceite" not in datos and ("cantidad de aceite" in texto_norm or "capacidad de aceite" in texto_norm):
            m_cap = re.search(
                r"(?:capacidad\s+de\s+aceite(?:\s+cambio)?|cambio(?:\s+de\s+aceite)?)\s*[:\-]?\s*(\d+(?:[.,]\d+)?)\s*(L|lt|cc|ml)\b",
                texto_raw,
                re.IGNORECASE,
            )
            if m_cap:
                num_str = m_cap.group(1).replace(",", ".")
                unidad = m_cap.group(2).upper()
                try:
                    num = float(num_str)
                    if re.match(r"^\d[.,]\d{3}$", m_cap.group(1)):
                        num = float(m_cap.group(1).replace(".", "").replace(",", ""))
                    litros = num / 1000.0 if unidad in ("CC", "ML") else num
                    if 0.4 <= litros <= 5.0 and m_cap.group(1) in texto_raw:
                        valor_str = f"{litros:.2f}".rstrip("0").rstrip(".") + " L"
                        datos["capacidad_aceite"] = {"valor": valor_str, "pagina": pno}
                except ValueError:
                    pass

        # 3. Líquido de frenos (DOT 3 / DOT 4 / DOT 3 o 4)
        if "liquido_frenos" not in datos and ("liquido de fren" in texto_norm or "aceite de freno" in texto_norm):
            m_dot = re.search(r"\b(DOT\s*[345](?:\.\d)?(?:\s*(?:o|y|/)\s*(?:DOT\s*)?[345])?)\b", texto_raw, re.IGNORECASE)
            if m_dot:
                val = re.sub(r"\s+", " ", m_dot.group(1).upper()).replace(" O ", " o ")
                if m_dot.group(1) in texto_raw:
                    datos["liquido_frenos"] = {"valor": val, "pagina": pno}

        # 4. Presión de llantas (psi)
        if ("presion_llanta_delantera" not in datos or "presion_llanta_trasera" not in datos) and ("presion" in texto_norm and "psi" in texto_norm):
            m_del = re.search(
                r"(?:llanta\s+delantera|rueda\s+delantera|delantera)\s*[:\-]?\s*"
                r"(?:\d+(?:[.,]\d+)?\s*kpa\s*\([^)]*?,\s*)?"
                r"(\d{2}(?:[.,]\d+)?)\s*psi\b",
                texto_raw,
                re.IGNORECASE,
            )
            if m_del:
                val_d = float(m_del.group(1).replace(",", "."))
                if 15 <= val_d <= 55:
                    datos.setdefault("presion_llanta_delantera", {"valor": f"{m_del.group(1).replace(',', '.')} psi", "pagina": pno})
            m_tras = re.search(
                r"(?:llanta\s+trasera|rueda\s+trasera|trasera|posterior)\s*"
                r"(?:\(?\s*s[oó]lo\s*\)?)?\s*[:\-]?\s*"
                r"(?:\d+(?:[.,]\d+)?\s*kpa\s*\([^)]*?,\s*)?"
                r"(\d{2}(?:[.,]\d+)?)\s*psi\b",
                texto_raw,
                re.IGNORECASE,
            )
            if m_tras:
                val_t = float(m_tras.group(1).replace(",", "."))
                if 15 <= val_t <= 60:
                    datos.setdefault("presion_llanta_trasera", {"valor": f"{m_tras.group(1).replace(',', '.')} psi", "pagina": pno})

        # 5. Holgura de cadena (ej. 25 - 35 mm, 40.0–45.0 mm)
        if "holgura_cadena" not in datos and ("holgura" in texto_norm or "tension de la cadena" in texto_norm or "juego de la cadena" in texto_norm) and "cadena" in texto_norm:
            m_cad = re.search(
                r"(?:holgura|juego|tensi[oó]n)[^.\n]{0,60}?\b(\d{1,2}(?:[.,]\d+)?)\s*[-–a]\s*(\d{1,2}(?:[.,]\d+)?)\s*mm\b",
                texto_raw,
                re.IGNORECASE,
            )
            if m_cad:
                a = float(m_cad.group(1).replace(",", "."))
                b = float(m_cad.group(2).replace(",", "."))
                if 5 <= a < b <= 65 and m_cad.group(1) in texto_raw and m_cad.group(2) in texto_raw:
                    datos["holgura_cadena"] = {"valor": _formatear_rango_mm(m_cad.group(1), m_cad.group(2)), "pagina": pno}

    return datos


# ---------------------------------------------------------------------------
# Punto de entrada por marca sobre archivo PDF
# ---------------------------------------------------------------------------

def extraer_desde_pdf(
    marca: str,
    modelo: str,
    url_pdf: str,
    dominios_permitidos: list[str],
    carpeta_cache: Path = CARPETA_CACHE_DEFECTO,
    robots_guard: RobotsGuard | None = None,
    rate_limiter: RateLimiter | None = None,
) -> tuple[dict[str, Any] | None, dict[str, Any] | None, str | None]:
    """Descarga y extrae (plan_mantenimiento, datos_servicio, motivo_si_omite) para una moto."""
    if not es_url_oficial(url_pdf, dominios_permitidos):
        return None, None, f"URL fuera de lista blanca o sin HTTPS: {url_pdf}"
    marca_low = marca.strip().lower()
    if marca_low not in ("yamaha", "bajaj"):
        return None, None, f"Sin plantilla de mantenimiento configurada para {marca}"

    ruta_pdf, sha256, err = obtener_pdf_oficial(
        url_pdf,
        dominios_permitidos,
        carpeta_cache=carpeta_cache,
        robots_guard=robots_guard,
        rate_limiter=rate_limiter,
    )
    if err or ruta_pdf is None or sha256 is None:
        return None, None, err or "No se pudo obtener el PDF"

    try:
        with pdfplumber.open(ruta_pdf) as pdf:
            if not pdf_tiene_texto_extraible(pdf):
                return None, None, "PDF sin texto extraíble (escaneado o en curvas)"

            titulo_meta = (pdf.metadata or {}).get("Title")
            titulo_doc = (
                titulo_meta.strip()
                if isinstance(titulo_meta, str)
                and len(titulo_meta.strip()) >= 5
                and not titulo_meta.strip().lower().endswith((".book", ".indd", ".pdf"))
                else f"Manual oficial {marca} {modelo}"
            )

            paginas_texto: list[tuple[int, str]] = []
            paginas_mant: list[tuple[int, str, list[list[list[str | None]]]]] = []

            for idx, page in enumerate(pdf.pages, start=1):
                txt = page.extract_text() or ""
                paginas_texto.append((idx, txt))
                low = normalizar_texto_pdf(txt)
                es_candidata = False
                if marca_low == "yamaha":
                    es_candidata = (
                        ("elemento" in low or "operacion" in low or "cuadro general de mantenimiento" in low)
                        and (
                            "cuentakil" in low
                            or "odometro" in low
                            or ("mantenimiento" in low and "km" in low)
                        )
                        and "tabla de contenidos" not in low
                    )
                elif marca_low == "bajaj":
                    es_candidata = (
                        "operacion" in low
                        and ("frecuencia recomendada" in low or "mantenimiento periodico" in low)
                    )
                if es_candidata:
                    if marca_low == "bajaj":
                        tablas = [
                            _reconstruir_filas_bajaj_sin_bordes(page, t)
                            for t in (page.find_tables() or [])
                        ]
                    else:
                        tablas = page.extract_tables() or []
                    celdas_txt = " ".join(
                        (c or "")
                        for tab in tablas
                        for row in tab
                        for c in row
                        if c
                    )
                    texto_cotejo = f"{_texto_palabras_pagina(page)} {celdas_txt}"
                    paginas_mant.append((idx, texto_cotejo, tablas))

            if marca_low == "yamaha":
                pag_ini, tareas = extraer_plan_yamaha_desde_paginas(paginas_mant)
                paginas_espec: list[tuple[int, str]] = []
                for idx, txt in paginas_texto:
                    low = normalizar_texto_pdf(txt)
                    if any(
                        k in low
                        for k in (
                            "especificaciones",
                            "presion de aire",
                            "holgura",
                            "juego de la cadena",
                            "liquido de fren",
                            "viscosidad",
                            "cantidad de aceite",
                        )
                    ):
                        flow_txt = pdf.pages[idx - 1].extract_text(use_text_flow=True) or txt
                        paginas_espec.append((idx, flow_txt))
                datos_servicio = extraer_datos_servicio_pdf(paginas_espec)
            else:
                pag_ini, tareas = extraer_plan_bajaj_desde_paginas(paginas_mant)
                paginas_espec = []
                for idx, txt in paginas_texto:
                    low = normalizar_texto_pdf(txt)
                    if any(
                        k in low
                        for k in (
                            "especificaciones",
                            "viscosidad",
                            "capacidad de aceite",
                            "holgura",
                            "psi",
                            "dot 4",
                            "dot4",
                            "presion rueda",
                            "presion de aire",
                        )
                    ):
                        flow_txt = pdf.pages[idx - 1].extract_text(use_text_flow=True) or txt
                        layout_txt = pdf.pages[idx - 1].extract_text(layout=True) or txt
                        paginas_espec.append((idx, flow_txt))
                        if layout_txt != flow_txt:
                            paginas_espec.append((idx, layout_txt))
                datos_servicio = extraer_datos_servicio_pdf(paginas_espec)

    except Exception as e:
        return None, None, f"Error procesando PDF: {e}"

    if not tareas or pag_ini is None:
        return None, datos_servicio or None, f"La tabla de mantenimiento no coincide con la plantilla de {marca}"

    plan = {
        "fuente": {
            "url": url_pdf,
            "sha256": sha256,
            "pagina": pag_ini,
            "titulo": titulo_doc,
        },
        "tareas": tareas,
    }
    return plan, datos_servicio or None, None
