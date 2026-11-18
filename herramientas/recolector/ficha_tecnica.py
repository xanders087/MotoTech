"""Extracción determinista, validación de rangos/unidades y versionado de fichas técnicas (sin IA).

Principio:
- Solo se mapean etiquetas oficiales presentes en el diccionario de sinónimos.
- Todo dato extraído debe aparecer literalmente en el contenido capturado de la fuente oficial.
- Si falla la verificación literal, de unidad o de rango plausible, el campo se omite.
"""
from __future__ import annotations

import datetime
import html as html_lib
import json
import re
import sys
import unicodedata
from pathlib import Path
from typing import Any

CARPETA_FICHAS_TOOLS = Path(__file__).resolve().parents[1] / "fichas"
if str(CARPETA_FICHAS_TOOLS) not in sys.path:
    sys.path.insert(0, str(CARPETA_FICHAS_TOOLS))

import validar  # noqa: E402

METODO_VERIFICACION = "extraccion determinista v1"

_RE_RELLENO = re.compile(
    r"^(todo|tbd|pendiente|por definir|n/?a|\?+|x+|-+|—+|\.+|sin dato)$",
    re.IGNORECASE,
)

# Etiqueta normalizada (sin tildes, sin signos de puntuación finales, sin unidad entre paréntesis) -> campo estándar.
SINONIMOS_ETIQUETA: dict[str, str] = {
    # Motor
    "cilindraje": "cilindraje",
    "cilindrada": "cilindraje",
    "desplazamiento": "cilindraje",
    "tipo de motor": "tipo_motor",
    "motor": "tipo_motor",
    "potencia maxima": "potencia_maxima",
    "potencia": "potencia_maxima",
    "potencia max": "potencia_maxima",
    "max. potencia": "potencia_maxima",
    "max potencia": "potencia_maxima",
    "potencia neta": "potencia_maxima",
    "torque maximo": "torque_maximo",
    "torque maxima": "torque_maximo",
    "torque": "torque_maximo",
    "max. torque": "torque_maximo",
    "max torque": "torque_maximo",
    "par motor": "torque_maximo",
    "par maximo": "torque_maximo",
    "torque max": "torque_maximo",
    "sistema de alimentacion": "alimentacion",
    "sistema de alimentacion de combustible": "alimentacion",
    "alimentacion": "alimentacion",
    "sistema de combustible": "alimentacion",
    "refrigeracion": "refrigeracion",
    "sistema de refrigeracion": "refrigeracion",
    "enfriamiento": "refrigeracion",
    "arranque": "arranque",
    "tipo de arranque": "arranque",
    "sistema de arranque": "arranque",
    "relacion de compresion": "relacion_compresion",
    "compresion": "relacion_compresion",
    # Transmisión
    "tipo de transmision": "tipo_transmision",
    "transmision": "tipo_transmision",
    "caja de cambios": "tipo_transmision",
    "numero de velocidades": "numero_velocidades",
    "velocidades": "numero_velocidades",
    "marchas": "numero_velocidades",
    "transmision final": "transmision_final",
    "sistema de reduccion secundaria": "transmision_final",
    # Chasis
    "freno delantero": "freno_delantero",
    "frenos delanteros": "freno_delantero",
    "tipo de freno delantero": "freno_delantero",
    "freno trasero": "freno_trasero",
    "frenos traseros": "freno_trasero",
    "tipo de freno trasero": "freno_trasero",
    "abs": "abs",
    "sistema abs": "abs",
    "frenos abs": "abs",
    "suspension delantera": "suspension_delantera",
    "tipo de suspension delantera": "suspension_delantera",
    "suspension trasera": "suspension_trasera",
    "tipo de suspension trasera": "suspension_trasera",
    "llanta delantera": "llanta_delantera",
    "rueda delantera": "llanta_delantera",
    "neumatico delantero": "llanta_delantera",
    "tamano de llanta delantera": "llanta_delantera",
    "llanta trasera": "llanta_trasera",
    "rueda trasera": "llanta_trasera",
    "neumatico trasero": "llanta_trasera",
    "tamano de llanta trasera": "llanta_trasera",
    # Dimensiones
    "capacidad de combustible": "capacidad_tanque",
    "capacidad del tanque": "capacidad_tanque",
    "capacidad tanque": "capacidad_tanque",
    "capacidad tanque combustible": "capacidad_tanque",
    "capacidad de tanque": "capacidad_tanque",
    "capacidad del tanque de combustible": "capacidad_tanque",
    "capacidad del tanque de gasolina": "capacidad_tanque",
    "tanque de combustible": "capacidad_tanque",
    "peso": "peso",
    "peso en seco": "peso",
    "peso en orden de marcha": "peso",
    "peso neto": "peso",
    "peso total": "peso",
    "peso sin carga": "peso",
    "altura al asiento": "altura_asiento",
    "altura del asiento": "altura_asiento",
    "altura al sillin": "altura_asiento",
    "distancia entre ejes": "distancia_ejes",
}

_RE_NUMERO = re.compile(r"(\d+(?:[.,]\d+)?)")
_RE_LLANTA = re.compile(r"(\d{2,3}\s*/\s*\d{2,3}|\d\.\d{2}\s*[-xX]\s*\d{2}|\d{2,3}\s*[-xX]\s*\d{2})")
_RE_COMPRESION = re.compile(r"(\d{1,2}(?:[.,]\d{1,2})?)\s*:\s*0?1\b")


def _sin_tildes(texto: str) -> str:
    return unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()


def _limpiar_espacios(texto: str) -> str:
    return re.sub(r"\s+", " ", html_lib.unescape(texto).replace("\xa0", " ")).strip()


def normalizar_etiqueta(etiqueta: str) -> tuple[str, str | None]:
    """Devuelve (etiqueta_limpia, pista_de_unidad_o_None)."""
    limpia = _sin_tildes(_limpiar_espacios(etiqueta)).lower().rstrip(".:;* ")
    pista_unidad: str | None = None
    paren = re.search(r"\(([^)]+)\)", limpia)
    if paren:
        contenido_paren = paren.group(1).strip()
        for candidato in ("gal", "mm", "cm", "kg", "lb", "cc", "cm3", "hp", "kw", "nm", "l"):
            if re.search(rf"\b{candidato}\b", contenido_paren):
                pista_unidad = candidato
                break
        if pista_unidad is None and "/" in contenido_paren:
            if "hp" in contenido_paren:
                pista_unidad = "hp"
            elif "kw" in contenido_paren:
                pista_unidad = "kw"
            elif "nm" in contenido_paren:
                pista_unidad = "nm"
    sin_paren = re.sub(r"\s*\([^)]*\)", "", limpia)
    sin_paren = re.sub(r"\s+", " ", sin_paren).strip(".:;* ")
    return sin_paren, pista_unidad


def mapear_etiqueta(etiqueta: str) -> tuple[str | None, str | None]:
    """Mapea una etiqueta oficial a (campo_estandar, pista_unidad). None si no se reconoce."""
    clave_norm, pista = normalizar_etiqueta(etiqueta)
    return SINONIMOS_ETIQUETA.get(clave_norm), pista


def aparece_en_fuente(texto_original: str, texto_fuente: str) -> bool:
    """Comprueba de forma literal que texto_original aparezca en la fuente capturada."""
    if not texto_original or not texto_fuente:
        return False
    aguja = _limpiar_espacios(texto_original).lower()
    pajar = _limpiar_espacios(texto_fuente).lower()
    return bool(aguja) and aguja in pajar


def _numero_float(raw: str) -> float | None:
    # Maneja separador de miles (ej. 8.000 rpm -> no usar ese, aquí se pasa el primer número del valor).
    limpio = raw.strip()
    # Si es formato N.NNN con 3 dígitos después del punto y sin más decimales (ej. 1.286 mm, 8.000)
    if re.match(r"^\d{1,2}\.\d{3}$", limpio):
        limpio = limpio.replace(".", "")
    elif re.match(r"^\d{1,2},\d{3}$", limpio):
        limpio = limpio.replace(",", "")
    else:
        limpio = limpio.replace(",", ".")
    try:
        return float(limpio)
    except ValueError:
        return None


def _formatear_num(val: float, sufijo: str, decimales: int = 1) -> str:
    red = round(val, decimales)
    if abs(red - round(red)) < 1e-6:
        return f"{int(round(red))} {sufijo}"
    return f"{red:.{decimales}f} {sufijo}"


def validar_y_normalizar_dato(
    campo: str,
    valor_raw: str,
    pista_unidad: str | None = None,
) -> tuple[str, str | None] | None:
    """Valida unidad y rango plausible. Devuelve (texto_original, valor_normalizado) o None."""
    texto = _limpiar_espacios(valor_raw)
    if not texto or len(texto) > 180 or "<" in texto or ">" in texto:
        return None
    if _RE_RELLENO.match(texto):
        return None

    low = _sin_tildes(texto).lower()

    if campo == "cilindraje":
        m = re.search(r"(\d{1,4}(?:[.,]\d{1,3})?)\s*(?:cc|c\.c\.|cm3)?\b", low)
        if not m:
            return None
        num = _numero_float(m.group(1))
        if num is None or not 40 <= num <= 2600:
            return None
        if not any(u in low for u in ("cc", "c.c", "cm3")) and pista_unidad not in ("cc", "cm3") and not re.match(r"^\d+(?:[.,]\d+)?$", low):
            return None
        return texto, f"{int(num)} cc"

    if campo == "potencia_maxima":
        tiene_unidad_texto = any(u in low for u in ("hp", "cv", "kw", "ps"))
        tiene_unidad = tiene_unidad_texto or pista_unidad in ("hp", "kw")
        m = _RE_NUMERO.search(low)
        if not m or not tiene_unidad:
            return None
        num = _numero_float(m.group(1))
        if num is None or not 1.0 <= num <= 350.0:
            return None
        if not tiene_unidad_texto and pista_unidad in ("hp", "kw"):
            sufijo = "kW" if pista_unidad == "kw" else "HP"
            return texto, f"{m.group(1).replace(',', '.')} {sufijo}"
        return texto, None

    if campo == "torque_maximo":
        tiene_unidad_texto = any(u in low for u in ("nm", "n.m", "n·m", "kg", "lb"))
        tiene_unidad = tiene_unidad_texto or pista_unidad == "nm"
        m = _RE_NUMERO.search(low)
        if not m or not tiene_unidad:
            return None
        num = _numero_float(m.group(1))
        if num is None or not 1.0 <= num <= 300.0:
            return None
        if not tiene_unidad_texto and pista_unidad == "nm":
            return texto, f"{m.group(1).replace(',', '.')} Nm"
        return texto, None

    if campo == "relacion_compresion":
        m = _RE_COMPRESION.search(low)
        if not m:
            return None
        num = _numero_float(m.group(1))
        if num is None or not 5.0 <= num <= 18.0:
            return None
        return texto, None

    if campo == "numero_velocidades":
        if any(k in low for k in ("automatica", "cvt", "v-matic")):
            return texto, None
        m = re.search(r"\b([1-7])\b", low)
        if not m:
            return None
        return texto, f"{m.group(1)} velocidades"

    if campo == "capacidad_tanque":
        # Primero buscar litros explícitos
        m_l = re.search(r"(\d+(?:[.,]\d+)?)\s*(?:l\b|lt|lts|litro)", low)
        m_gal = re.search(r"(\d+(?:[.,]\d+)?)\s*(?:gal)", low)
        if m_l:
            litros = _numero_float(m_l.group(1))
            if litros is None or not 1.5 <= litros <= 35.0:
                return None
            return texto, _formatear_num(litros, "L")
        if m_gal or pista_unidad == "gal":
            m = m_gal or _RE_NUMERO.search(low)
            if not m:
                return None
            gal = _numero_float(m.group(1))
            if gal is None:
                return None
            litros = gal * 3.78541
            if not 1.5 <= litros <= 35.0:
                return None
            return texto, _formatear_num(litros, "L")
        if pista_unidad == "l" and re.match(r"^\d+(?:[.,]\d+)?$", low):
            litros = _numero_float(low)
            if litros is None or not 1.5 <= litros <= 35.0:
                return None
            return texto, _formatear_num(litros, "L")
        return None

    if campo == "peso":
        m_kg = re.search(r"(\d+(?:[.,]\d+)?)\s*(?:kg|kilogram)", low)
        m_lb = re.search(r"(\d+(?:[.,]\d+)?)\s*(?:lb|libra)", low)
        if m_kg or (pista_unidad == "kg" and _RE_NUMERO.search(low)):
            m = m_kg or _RE_NUMERO.search(low)
            kg = _numero_float(m.group(1)) if m else None
            if kg is None or not 35.0 <= kg <= 450.0:
                return None
            return texto, _formatear_num(kg, "kg")
        if m_lb:
            lb = _numero_float(m_lb.group(1))
            if lb is None:
                return None
            kg = lb * 0.453592
            if not 35.0 <= kg <= 450.0:
                return None
            return texto, _formatear_num(kg, "kg")
        return None

    if campo in ("altura_asiento", "distancia_ejes"):
        min_mm, max_mm = (450.0, 1050.0) if campo == "altura_asiento" else (800.0, 2000.0)
        m_mm = re.search(r"(\d+(?:[.,]\d+)?)\s*mm\b", low)
        m_cm = re.search(r"(\d+(?:[.,]\d+)?)\s*cm\b", low)
        if m_mm or (pista_unidad == "mm" and _RE_NUMERO.search(low)):
            m = m_mm or _RE_NUMERO.search(low)
            mm = _numero_float(m.group(1)) if m else None
        elif m_cm or (pista_unidad == "cm" and _RE_NUMERO.search(low)):
            m = m_cm or _RE_NUMERO.search(low)
            cm = _numero_float(m.group(1)) if m else None
            mm = cm * 10.0 if cm is not None else None
        else:
            return None
        if mm is None or not min_mm <= mm <= max_mm:
            return None
        return texto, f"{int(round(mm))} mm"

    if campo in ("llanta_delantera", "llanta_trasera"):
        if not _RE_LLANTA.search(low):
            return None
        return texto, None

    # Campos descriptivos de texto (tipo_motor, frenos, suspensión, transmisión, abs...)
    if len(texto) < 2:
        return None
    return texto, None


def construir_ficha_tecnica(
    pares: list[tuple[str, str]],
    url_fuente: str,
    texto_fuente: str,
    hoy: str,
) -> dict[str, dict[str, Any]]:
    """Convierte pares (etiqueta, valor) oficiales en un bloque ficha_tecnica validado."""
    if not url_fuente.startswith("https://"):
        return {}
    resultado: dict[str, dict[str, Any]] = {}
    for etiqueta, valor_raw in pares:
        campo, pista_unidad = mapear_etiqueta(etiqueta)
        if campo is None or campo in resultado:
            continue
        validado = validar_y_normalizar_dato(campo, valor_raw, pista_unidad)
        if validado is None:
            continue
        texto_original, valor_normalizado = validado
        if not aparece_en_fuente(texto_original, texto_fuente):
            continue
        entrada: dict[str, Any] = {
            "texto_original": texto_original,
            "fuente": url_fuente,
            "capturado": hoy,
        }
        if valor_normalizado and valor_normalizado.lower() != texto_original.lower():
            entrada["valor_normalizado"] = valor_normalizado
        resultado[campo] = entrada
    return resultado


def extraer_anio_explicito(valor: Any, hoy: datetime.date | None = None) -> int | None:
    """Extrae un año explícito (1980..hoy+1) si la fuente oficial lo publica en su campo de año/modelo."""
    fecha_ref = hoy or datetime.date.today()
    if isinstance(valor, int) and not isinstance(valor, bool):
        anio = valor
    elif isinstance(valor, str):
        m = re.fullmatch(r"\s*(20\d{2}|19\d{2})\s*", valor)
        if not m:
            return None
        anio = int(m.group(1))
    else:
        return None
    return anio if 1980 <= anio <= fecha_ref.year + 1 else None


def _firma_especificaciones(ficha: dict[str, Any]) -> dict[str, str]:
    """Firma comparable de ficha_tecnica (ignora fechas de captura para evitar commits vacíos)."""
    tecnica = ficha.get("ficha_tecnica")
    if not isinstance(tecnica, dict):
        return {}
    firma: dict[str, str] = {}
    for k, v in tecnica.items():
        if isinstance(v, dict) and v.get("texto_original"):
            firma[k] = _limpiar_espacios(str(v["texto_original"]))
    return firma


def sincronizar_ficha_en_carpeta(
    carpeta_fichas: Path,
    marca: str,
    modelo: str,
    cilindraje_cc: int,
    ficha_tecnica: dict[str, dict[str, Any]],
    url_manual: str | None = None,
    anio_fuente: int | None = None,
    plan_mantenimiento: dict[str, Any] | None = None,
    datos_servicio: dict[str, Any] | None = None,
    hoy: datetime.date | None = None,
) -> list[dict[str, Any]]:
    """Aplica las reglas de versionado por año (§4.1) en `fichas/` y devuelve las fichas nuevas o cambiadas."""
    if not ficha_tecnica:
        return []
    fecha_hoy = hoy or datetime.date.today()
    hoy_str = fecha_hoy.isoformat()
    anio_objetivo = anio_fuente if anio_fuente is not None else fecha_hoy.year
    if not 1950 <= anio_objetivo <= fecha_hoy.year + 1:
        anio_objetivo = fecha_hoy.year
    anio_origen = "fuente" if anio_fuente is not None else "deteccion"

    carpeta_fichas.mkdir(parents=True, exist_ok=True)
    clave_moto = validar.clave(marca, modelo, cilindraje_cc)

    existentes: list[tuple[Path, dict[str, Any]]] = []
    fichas_actuales, _ = validar.cargar_fichas(carpeta_fichas)
    for ruta, f in fichas_actuales.items():
        if (
            isinstance(f.get("marca"), str)
            and isinstance(f.get("modelo"), str)
            and isinstance(f.get("cilindraje_cc"), int)
            and validar.clave(f["marca"], f["modelo"], f["cilindraje_cc"]) == clave_moto
        ):
            existentes.append((ruta, f))
    existentes.sort(key=lambda par: (par[1].get("anio_hasta", 0), par[1].get("anio_desde", 0)))

    def _construir_doc(
        desde: int,
        hasta: int,
        origen: str,
        plan: dict[str, Any] | None = plan_mantenimiento,
        servicio: dict[str, Any] | None = datos_servicio,
    ) -> dict[str, Any]:
        doc: dict[str, Any] = {
            "marca": marca,
            "modelo": modelo,
            "cilindraje_cc": cilindraje_cc,
            "anio_desde": desde,
            "anio_hasta": hasta,
            "anio_origen": origen,
            "ficha_tecnica": ficha_tecnica,
            "url_manual": url_manual,
            "verificacion": {"metodo": METODO_VERIFICACION, "fecha": hoy_str},
        }
        if plan is not None:
            doc["plan_mantenimiento"] = plan
        if servicio is not None:
            doc["datos_servicio"] = servicio
        return doc

    def _guardar(doc: dict[str, Any], ruta_anterior: Path | None = None) -> dict[str, Any] | None:
        if validar.validar_ficha(doc, fecha_hoy):
            return None
        destino = carpeta_fichas / f"{validar.id_ficha(doc)}.json"
        if ruta_anterior is not None and ruta_anterior != destino and ruta_anterior.exists():
            ruta_anterior.unlink()
        destino.write_text(
            json.dumps(doc, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        return doc

    if not existentes:
        nueva = _construir_doc(anio_objetivo, anio_objetivo, anio_origen)
        guardada = _guardar(nueva)
        return [guardada] if guardada else []

    ruta_ult, ult = existentes[-1]
    firma_nueva = {k: _limpiar_espacios(str(v["texto_original"])) for k, v in ficha_tecnica.items()}
    firma_ult = _firma_especificaciones(ult)
    cambio_specs = firma_nueva != firma_ult
    cambio_manual = (url_manual is not None and url_manual != ult.get("url_manual"))
    cambio_plan = (plan_mantenimiento is not None and plan_mantenimiento != ult.get("plan_mantenimiento"))
    cambio_servicio = (datos_servicio is not None and datos_servicio != ult.get("datos_servicio"))

    if not cambio_specs:
        necesita_guardar = False
        actualizada = json.loads(json.dumps(ult))
        if anio_objetivo > int(ult.get("anio_hasta", 0)):
            actualizada["anio_hasta"] = anio_objetivo
            if anio_origen == "fuente":
                actualizada["anio_origen"] = "fuente"
            necesita_guardar = True
        if cambio_manual:
            actualizada["url_manual"] = url_manual
            necesita_guardar = True
        if cambio_plan:
            actualizada["plan_mantenimiento"] = plan_mantenimiento
            necesita_guardar = True
        if cambio_servicio:
            actualizada["datos_servicio"] = datos_servicio
            necesita_guardar = True
        if not necesita_guardar:
            return []
        if "verificacion" in actualizada:
            actualizada["verificacion"] = {"metodo": METODO_VERIFICACION, "fecha": hoy_str}
        guardada = _guardar(actualizada, ruta_anterior=ruta_ult)
        return [guardada] if guardada else []

    # Los valores cambiaron: cerrar ficha vigente y crear nueva versión (sin solapar años).
    ult_desde = int(ult["anio_desde"])
    ult_hasta = int(ult["anio_hasta"])
    modificadas: list[dict[str, Any]] = []
    plan_heredado = plan_mantenimiento if plan_mantenimiento is not None else ult.get("plan_mantenimiento")
    servicio_heredado = datos_servicio if datos_servicio is not None else ult.get("datos_servicio")
    if ult_desde < anio_objetivo:
        nuevo_hasta_anterior = min(ult_hasta, anio_objetivo - 1)
        if nuevo_hasta_anterior != ult_hasta:
            cerrada = json.loads(json.dumps(ult))
            cerrada["anio_hasta"] = nuevo_hasta_anterior
            guardada_cerrada = _guardar(cerrada, ruta_anterior=ruta_ult)
            if guardada_cerrada:
                modificadas.append(guardada_cerrada)
        nueva = _construir_doc(anio_objetivo, anio_objetivo, anio_origen, plan=plan_heredado, servicio=servicio_heredado)
        guardada_nueva = _guardar(nueva)
        if guardada_nueva:
            modificadas.append(guardada_nueva)
        return modificadas

    # Cambio dentro del mismo año (ult_desde >= anio_objetivo): actualizar la versión de ese año.
    reemplazo = _construir_doc(
        ult_desde,
        max(ult_hasta, anio_objetivo),
        anio_origen,
        plan=plan_heredado,
        servicio=servicio_heredado,
    )
    guardada = _guardar(reemplazo, ruta_anterior=ruta_ult)
    return [guardada] if guardada else []
