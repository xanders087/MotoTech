"""Adaptadores oficiales por marca para el recolector de catálogo y fichas técnicas.

Principio: el cilindraje nunca se infiere del nombre del modelo; siempre se extrae
de una etiqueta explícita de ficha técnica o se descarta si no está presente.
Los campos de ficha técnica se extraen únicamente por diccionario de sinónimos y
verificación literal + rangos/unidades sobre el contenido oficial capturado.
"""
from __future__ import annotations

import datetime
import json
import logging
import re
from dataclasses import dataclass, replace
from typing import Any
from urllib.parse import urljoin, urlparse

from bs4 import BeautifulSoup

from ficha_tecnica import construir_ficha_tecnica, extraer_anio_explicito
from robots_guard import OficialHttpClient, es_url_oficial

logger = logging.getLogger("recolector")

YAMAHA_REST_API_KEY = "5434ad6c-605c-4fe9-b73f-d5c085e35da8"

_EXCLUIR_NO_MOTO = re.compile(
    r"\b(cuatrimotos?|atvs?|sxs|side[\s-]*by[\s-]*side|utvs?|motocarros?|cargueros?|kargueros?|tuk[\s-]*tuk|torito|maxima|qute|pioneer|talon|trx\w*|yfm\w*|kodiak|wolverine|grizzly|viking)\b",
    re.IGNORECASE,
)

_RE_CC_VALOR = re.compile(
    r"(\d{1,4}(?:[.,]\d{1,3})?)\s*(?:cc|c\.c\.|cm3|cm³)\b",
    re.IGNORECASE,
)
_RE_DESPUES_CILINDRAJE = re.compile(
    r"cilindraj\w*\s*[:\-]?\s*(\d{1,4}(?:[.,]\d{1,3})?)\s*(?:cc|c\.c\.|cm3|cm³)?",
    re.IGNORECASE,
)
_RE_ANTES_CILINDRAJE = re.compile(
    r"(\d{1,4}(?:[.,]\d{1,3})?)\s*(?:cc|c\.c\.|cm3|cm³)\s*cilindraj\w*",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class ModeloOficial:
    modelo: str
    cilindraje_cc: int
    categoria: str | None
    url_fuente: str
    url_manual: str | None = None
    ficha_tecnica: dict[str, dict[str, Any]] | None = None
    anio_fuente: int | None = None


def parsear_cc(texto: str | None) -> int | None:
    """Extrae un entero en cc desde una cadena explícita de cilindrada (p. ej. '109,19 cm³' o '1.084 cm³')."""
    if not isinstance(texto, str) or not texto.strip():
        return None
    m = _RE_CC_VALOR.search(texto)
    if not m:
        m = re.search(r"^\s*(\d{1,4}(?:[.,]\d{1,3})?)\s*$", texto)
    if not m:
        return None
    num_str = m.group(1)
    if re.match(r"^\d{1,2}[.,]\d{3}$", num_str):
        num_str = num_str.replace(".", "").replace(",", "")
    else:
        num_str = num_str.replace(",", ".")
    try:
        valor = int(float(num_str))
    except ValueError:
        return None
    return valor if 40 <= valor <= 2600 else None


def _limpiar_espacios(texto: str | None) -> str:
    if not texto:
        return ""
    return re.sub(r"\s+", " ", texto).strip()


def _es_no_motocicleta(*textos: str | None) -> bool:
    combinado = " ".join(t for t in textos if t)
    return bool(_EXCLUIR_NO_MOTO.search(combinado))


def _absolutizar(href: str, base_url: str, dominios: list[str]) -> str | None:
    if not href:
        return None
    absoluta = urljoin(base_url, href.strip()).split("#", 1)[0]
    return absoluta if es_url_oficial(absoluta, dominios) else None


def _pares_desde_tablas_html(soup: BeautifulSoup) -> list[tuple[str, str]]:
    pares: list[tuple[str, str]] = []
    for tr in soup.find_all("tr"):
        celdas = tr.find_all(["th", "td"])
        if len(celdas) >= 2:
            etiqueta = _limpiar_espacios(celdas[0].get_text(" ", strip=True))
            valor = _limpiar_espacios(celdas[1].get_text(" ", strip=True))
            if etiqueta and valor:
                pares.append((etiqueta, valor))
    return pares


# ---------------------------------------------------------------------------
# Yamaha (incolmotos-yamaha.com.co)
# ---------------------------------------------------------------------------

def parsear_yamaha_sitemap(xml: str, dominios: list[str] | None = None) -> list[str]:
    doms = dominios or ["incolmotos-yamaha.com.co"]
    urls = re.findall(r"<loc>\s*(https://[^<\s]+)\s*</loc>", xml, re.IGNORECASE)
    vistos: set[str] = set()
    resultado: list[str] = []
    for u in urls:
        if "/vehiculo/" not in u or not es_url_oficial(u, doms):
            continue
        if _es_no_motocicleta(u):
            continue
        if u not in vistos:
            vistos.add(u)
            resultado.append(u)
    return resultado


def parsear_yamaha_catalogo_links(html: str, base_url: str, dominios: list[str] | None = None) -> list[str]:
    doms = dominios or ["incolmotos-yamaha.com.co"]
    soup = BeautifulSoup(html, "html.parser")
    vistos: set[str] = set()
    resultado: list[str] = []
    for a in soup.find_all("a", href=True):
        u = _absolutizar(a["href"], base_url, doms)
        if not u or "/vehiculo/" not in u:
            continue
        if _es_no_motocicleta(u, a.get_text(" ", strip=True)):
            continue
        if u not in vistos:
            vistos.add(u)
            resultado.append(u)
    return resultado


def _yamaha_extraer_post_id(url: str) -> str | None:
    m = re.search(r"/vehiculo/[^/]+/(\d+)/?", url)
    return m.group(1) if m else None


def _yamaha_manual_desde_detail(detail: dict[str, Any], dominios: list[str]) -> str | None:
    manuales = detail.get("manual")
    if not isinstance(manuales, list):
        return None
    candidato_secundario: str | None = None
    for item in manuales:
        if not isinstance(item, dict):
            continue
        nombre = _limpiar_espacios(str(item.get("nombre_manual") or ""))
        pdf = item.get("manual_en_pdf")
        if isinstance(pdf, dict):
            url = pdf.get("url")
            if isinstance(url, str) and es_url_oficial(url, dominios) and ".pdf" in url.lower():
                if "propietario" in nombre.lower() or "usuario" in nombre.lower():
                    return url
                if candidato_secundario is None and "catálogo de partes" not in nombre.lower():
                    candidato_secundario = url
    return candidato_secundario


def _yamaha_pares_y_texto_desde_detail(detail: dict[str, Any]) -> tuple[list[tuple[str, str]], str]:
    pares: list[tuple[str, str]] = []
    trozos_texto: list[str] = []

    for clave_html in ("especificaciones_completas", "resumen_especificaciones"):
        bloque = detail.get(clave_html)
        if isinstance(bloque, str) and bloque.strip():
            soup = BeautifulSoup(bloque, "html.parser")
            pares.extend(_pares_desde_tablas_html(soup))
            trozos_texto.append(soup.get_text(" ", strip=True))
        elif isinstance(bloque, list):
            for fila in bloque:
                if isinstance(fila, dict):
                    etiq = _limpiar_espacios(str(fila.get("nombre") or fila.get("titulo") or fila.get("label") or ""))
                    val = _limpiar_espacios(str(fila.get("valor") or fila.get("value") or ""))
                    if etiq and val:
                        pares.append((etiq, val))
                    trozos_texto.append(" ".join(str(v) for v in fila.values() if v is not None))

    generales = detail.get("especificaciones_generales")
    if isinstance(generales, list):
        for fila in generales:
            if isinstance(fila, dict):
                etiq = _limpiar_espacios(str(fila.get("nombre") or ""))
                val = _limpiar_espacios(str(fila.get("valor") or ""))
                if etiq and val:
                    pares.append((etiq, val))
                    trozos_texto.append(f"{etiq} {val}")

    return pares, " ".join(trozos_texto)


def parsear_yamaha_vehiculo_api(
    data: dict[str, Any],
    url_fuente: str,
    dominios: list[str] | None = None,
    hoy: str | None = None,
) -> ModeloOficial | None:
    doms = dominios or ["incolmotos-yamaha.com.co"]
    if not isinstance(data, dict):
        return None
    nombre = _limpiar_espacios(data.get("post_title"))
    detail = data.get("detail") if isinstance(data.get("detail"), dict) else {}
    categoria = _limpiar_espacios(
        detail.get("tipo_de_vehiculo") or data.get("categoria")
    ) or None
    if categoria and categoria.lower() in ("cbu", "ckd"):
        categoria = None
    if not nombre or _es_no_motocicleta(nombre, categoria, url_fuente):
        return None

    cc = None
    for texto_cc in (
        detail.get("cilindraje_especificaciones"),
        detail.get("cilindraje"),
    ):
        cc = parsear_cc(str(texto_cc) if texto_cc is not None else None)
        if cc is not None:
            break

    pares, texto_fuente = _yamaha_pares_y_texto_desde_detail(detail)
    if cc is None:
        for etiq, val in pares:
            if "cilindraj" in etiq.lower():
                cc = parsear_cc(val)
                if cc is not None:
                    break
        if cc is None and "cilindraj" in texto_fuente.lower():
            cc = parsear_cc(texto_fuente)

    if cc is None:
        return None

    hoy_str = hoy or datetime.date.today().isoformat()
    ficha = construir_ficha_tecnica(pares, url_fuente, texto_fuente, hoy_str) if pares else {}

    return ModeloOficial(
        modelo=nombre,
        cilindraje_cc=cc,
        categoria=categoria,
        url_fuente=url_fuente,
        url_manual=_yamaha_manual_desde_detail(detail, doms),
        ficha_tecnica=ficha or None,
        anio_fuente=extraer_anio_explicito(detail.get("modelo")),
    )


def parsear_yamaha_vehiculo_html(
    html: str,
    url_fuente: str,
    dominios: list[str] | None = None,
    hoy: str | None = None,
) -> ModeloOficial | None:
    doms = dominios or ["incolmotos-yamaha.com.co"]
    soup = BeautifulSoup(html, "html.parser")
    h1 = soup.find("h1")
    nombre = _limpiar_espacios(h1.get_text(" ", strip=True)) if h1 else ""
    if not nombre and soup.title:
        nombre = _limpiar_espacios(soup.title.get_text(" ", strip=True).split("-")[0])
    if not nombre or _es_no_motocicleta(nombre, url_fuente):
        return None

    texto = soup.get_text(" ", strip=True)
    cc = None
    for pat in (_RE_ANTES_CILINDRAJE, _RE_DESPUES_CILINDRAJE):
        for m in pat.finditer(texto):
            cc = parsear_cc(f"{m.group(1)} cc")
            if cc is not None:
                break
        if cc is not None:
            break
    if cc is None:
        return None

    categoria = None
    for a in soup.find_all("a", href=True):
        if "/categoria/" in a["href"]:
            txt = _limpiar_espacios(a.get_text(" ", strip=True))
            if txt and not _es_no_motocicleta(txt):
                categoria = txt
                break
            elif _es_no_motocicleta(a["href"], txt):
                return None

    url_manual = None
    for a in soup.find_all("a", href=True):
        href = _absolutizar(a["href"], url_fuente, doms)
        if href and ".pdf" in href.lower() and "manual" in (href + a.get_text(" ", strip=True)).lower():
            url_manual = href
            break

    hoy_str = hoy or datetime.date.today().isoformat()
    pares = _pares_desde_tablas_html(soup)
    ficha = construir_ficha_tecnica(pares, url_fuente, texto, hoy_str) if pares else {}

    return ModeloOficial(
        modelo=nombre,
        cilindraje_cc=cc,
        categoria=categoria,
        url_fuente=url_fuente,
        url_manual=url_manual,
        ficha_tecnica=ficha or None,
    )


# ---------------------------------------------------------------------------
# Honda (motos.honda.com.co)
# ---------------------------------------------------------------------------

def _iter_dicts_con_pdp(obj: Any):
    if isinstance(obj, dict):
        if "name" in obj and "pdpUrl" in obj and "displacement" in obj:
            yield obj
        for v in obj.values():
            yield from _iter_dicts_con_pdp(v)
    elif isinstance(obj, list):
        for item in obj:
            yield from _iter_dicts_con_pdp(item)


def _iter_honda_spec_items(obj: Any):
    if isinstance(obj, dict):
        if isinstance(obj.get("label"), str) and isinstance(obj.get("value"), str) and (
            str(obj.get("id", "")).startswith("spec-") or "iconAlt" in obj
        ):
            yield obj["label"], obj["value"]
        for v in obj.values():
            yield from _iter_honda_spec_items(v)
    elif isinstance(obj, list):
        for item in obj:
            yield from _iter_honda_spec_items(item)


def parsear_honda_catalogo_html(
    html: str,
    base_url: str = "https://motos.honda.com.co/motos-honda",
    dominios: list[str] | None = None,
) -> list[ModeloOficial]:
    doms = dominios or ["motos.honda.com.co", "d3lewjhzzgclom.cloudfront.net"]
    soup = BeautifulSoup(html, "html.parser")
    candidatos: list[dict[str, Any]] = []

    for script in soup.find_all("script"):
        raw = script.string or script.get_text() or ""
        if "pdpUrl" not in raw or "displacement" not in raw:
            continue
        for m in re.finditer(r'self\.__next_f\.push\(\[\s*\d+\s*,\s*("(?:\\.|[^"\\])*")\s*\]\)', raw):
            try:
                payload = json.loads(m.group(1))
            except json.JSONDecodeError:
                continue
            for idx_m in re.finditer(r'\{"[^"]+"\s*:', payload):
                start = idx_m.start()
                if "pdpUrl" not in payload[start : start + 800]:
                    continue
                try:
                    parsed, _ = json.JSONDecoder().raw_decode(payload[start:])
                    candidatos.extend(_iter_dicts_con_pdp(parsed))
                except json.JSONDecodeError:
                    continue

        if raw.lstrip().startswith(("{", "[")):
            try:
                candidatos.extend(_iter_dicts_con_pdp(json.loads(raw)))
            except json.JSONDecodeError:
                pass

    vistos: set[str] = set()
    resultado: list[ModeloOficial] = []
    for item in candidatos:
        nombre = _limpiar_espacios(str(item.get("name") or ""))
        pdp = str(item.get("pdpUrl") or "").strip()
        disp = str(item.get("displacement") or "")
        categoria = _limpiar_espacios(str(item.get("category") or "")) or None
        if not nombre or not pdp:
            continue
        if _es_no_motocicleta(nombre, categoria, pdp):
            continue
        cc = parsear_cc(disp)
        if cc is None:
            continue
        url_fuente = _absolutizar(pdp, base_url, doms)
        if not url_fuente:
            continue
        manual_raw = str(item.get("manualUrl") or "").strip()
        url_manual = _absolutizar(manual_raw, base_url, doms) if manual_raw else None
        clave_unica = f"{nombre.lower()}|{cc}"
        if clave_unica in vistos:
            continue
        vistos.add(clave_unica)
        resultado.append(
            ModeloOficial(
                modelo=nombre,
                cilindraje_cc=cc,
                categoria=categoria,
                url_fuente=url_fuente,
                url_manual=url_manual,
            )
        )
    return resultado


def parsear_honda_modelo_html(
    html: str,
    url_fuente: str,
    hoy: str | None = None,
) -> dict[str, dict[str, Any]]:
    """Extrae especificaciones técnicas desde la página de detalle (Next.js Flight o tablas HTML) de Honda."""
    soup = BeautifulSoup(html, "html.parser")
    pares: list[tuple[str, str]] = []
    trozos_texto: list[str] = [soup.get_text(" ", strip=True)]

    for script in soup.find_all("script"):
        raw = script.string or script.get_text() or ""
        if "spec-" not in raw and '"sections"' not in raw:
            continue
        for m in re.finditer(r'self\.__next_f\.push\(\[\s*\d+\s*,\s*("(?:\\.|[^"\\])*")\s*\]\)', raw):
            try:
                payload = json.loads(m.group(1))
            except json.JSONDecodeError:
                continue
            if '"sections"' not in payload and '"spec-' not in payload:
                continue
            trozos_texto.append(payload)
            for idx_m in re.finditer(r'\{"id"\s*:\s*"specs?-\d+"', payload):
                start = idx_m.start()
                try:
                    parsed, _ = json.JSONDecoder().raw_decode(payload[start:])
                    pares.extend(_iter_honda_spec_items(parsed))
                except json.JSONDecodeError:
                    continue
        if raw.lstrip().startswith(("{", "[")):
            try:
                trozos_texto.append(raw)
                pares.extend(_iter_honda_spec_items(json.loads(raw)))
            except json.JSONDecodeError:
                pass

    pares.extend(_pares_desde_tablas_html(soup))
    hoy_str = hoy or datetime.date.today().isoformat()
    return construir_ficha_tecnica(pares, url_fuente, " ".join(trozos_texto), hoy_str)


# ---------------------------------------------------------------------------
# Bajaj (grupouma.com)
# ---------------------------------------------------------------------------

def parsear_bajaj_catalogo_links(
    html: str,
    base_url: str = "https://grupouma.com/colombia/motos/",
    dominios: list[str] | None = None,
) -> list[str]:
    doms = dominios or ["grupouma.com", "colombia.globalbajaj.com"]
    soup = BeautifulSoup(html, "html.parser")
    vistos: set[str] = set()
    resultado: list[str] = []
    for a in soup.find_all("a", href=True):
        u = _absolutizar(a["href"], base_url, doms)
        if not u or "/colombia/motos/" not in u:
            continue
        path = urlparse(u).path.strip("/")
        partes = [p for p in path.split("/") if p]
        if len(partes) < 4:
            continue
        if _es_no_motocicleta(u, a.get_text(" ", strip=True)):
            continue
        norm = u.rstrip("/") + "/"
        if norm not in vistos:
            vistos.add(norm)
            resultado.append(norm)
    return resultado


def _bajaj_pares_desde_soup(soup: BeautifulSoup) -> list[tuple[str, str]]:
    pares: list[tuple[str, str]] = []
    eje_actual: str | None = None
    bloques = soup.select(".dipl_tab_desc p") or soup.find_all("p")
    for p in bloques:
        texto_p = _limpiar_espacios(p.get_text(" ", strip=True))
        if not texto_p or ":" not in texto_p:
            continue
        strong = p.find("strong")
        if strong and ":" in strong.get_text():
            etiq = _limpiar_espacios(strong.get_text(" ", strip=True)).rstrip(":")
            val = _limpiar_espacios(texto_p[len(_limpiar_espacios(strong.get_text(" ", strip=True))):]).lstrip(":").strip()
        else:
            partes = texto_p.split(":", 1)
            etiq = _limpiar_espacios(partes[0])
            val = _limpiar_espacios(partes[1])
        if not etiq or not val or len(etiq) > 60:
            continue
        low_etiq = etiq.lower()
        if "delanter" in low_etiq:
            eje_actual = "delantera"
        elif "traser" in low_etiq:
            eje_actual = "trasera"
        if low_etiq == "llanta" and eje_actual in ("delantera", "trasera"):
            etiq = f"Llanta {eje_actual}"
        pares.append((etiq, val))
    pares.extend(_pares_desde_tablas_html(soup))
    return pares


def parsear_bajaj_modelo_html(
    html: str,
    url_fuente: str,
    dominios: list[str] | None = None,
    hoy: str | None = None,
) -> ModeloOficial | None:
    doms = dominios or ["grupouma.com", "colombia.globalbajaj.com"]
    # Bajaj incluye una tabla comparativa con motos de la competencia después de esta frase.
    corte_html = html.lower().find("descubre por qué somos mejores")
    html_propio = html[:corte_html] if corte_html > 0 else html
    soup_full = BeautifulSoup(html, "html.parser")
    soup = BeautifulSoup(html_propio, "html.parser")

    h1 = soup.find("h1") or soup_full.find("h1")
    nombre = _limpiar_espacios(h1.get_text(" ", strip=True)) if h1 else ""
    if not nombre and soup_full.title:
        nombre = _limpiar_espacios(soup_full.title.get_text(" ", strip=True).split("|")[0].split("-")[0])
    if not nombre or _es_no_motocicleta(nombre, url_fuente):
        return None

    texto_propio = soup.get_text(" ", strip=True)
    cc = None
    for pat in (_RE_DESPUES_CILINDRAJE, _RE_ANTES_CILINDRAJE):
        for m in pat.finditer(texto_propio):
            cc = parsear_cc(f"{m.group(1)} cc")
            if cc is not None:
                break
        if cc is not None:
            break
    if cc is None:
        return None

    partes = [p for p in urlparse(url_fuente).path.strip("/").split("/") if p]
    categoria = partes[2].replace("-", " ").title() if len(partes) >= 4 else None

    url_manual = None
    for a in soup_full.find_all("a", href=True):
        href = _absolutizar(a["href"], url_fuente, doms)
        if not href:
            continue
        if ".pdf" in href.lower() and (
            "manual" in href.lower()
            or "mu-" in href.lower()
            or "manual" in a.get_text(" ", strip=True).lower()
        ):
            url_manual = href
            break

    hoy_str = hoy or datetime.date.today().isoformat()
    pares = _bajaj_pares_desde_soup(soup)
    ficha = construir_ficha_tecnica(pares, url_fuente, texto_propio, hoy_str) if pares else {}

    return ModeloOficial(
        modelo=nombre,
        cilindraje_cc=cc,
        categoria=categoria,
        url_fuente=url_fuente,
        url_manual=url_manual,
        ficha_tecnica=ficha or None,
    )


# ---------------------------------------------------------------------------
# AKT (aktmotos.com)
# ---------------------------------------------------------------------------

def parsear_akt_catalogo_links(
    html: str,
    base_url: str = "https://aktmotos.com/motos-akt/",
    dominios: list[str] | None = None,
) -> list[str]:
    doms = dominios or ["aktmotos.com"]
    soup = BeautifulSoup(html, "html.parser")
    vistos: set[str] = set()
    resultado: list[str] = []
    for a in soup.find_all("a", href=True):
        u = _absolutizar(a["href"], base_url, doms)
        if not u or "/motos-akt/" not in u:
            continue
        partes = [p for p in urlparse(u).path.strip("/").split("/") if p]
        if len(partes) < 3:
            continue
        if _es_no_motocicleta(u, a.get_text(" ", strip=True)):
            continue
        norm = u.rstrip("/") + "/"
        if norm not in vistos:
            vistos.add(norm)
            resultado.append(norm)
    return resultado


def _akt_pares_desde_soup(soup: BeautifulSoup) -> list[tuple[str, str]]:
    pares: list[tuple[str, str]] = []
    for col in soup.select(".columnaSpec"):
        ps = col.find_all("p")
        i = 0
        while i + 1 < len(ps):
            clases_a = ps[i].get("class") or []
            clases_b = ps[i + 1].get("class") or []
            if "titSpec" in clases_a and "desSpec" in clases_b:
                val = _limpiar_espacios(ps[i].get_text(" ", strip=True))
                etiq = _limpiar_espacios(ps[i + 1].get_text(" ", strip=True))
                if etiq and val:
                    pares.append((etiq, val))
                i += 2
            elif "desSpec" in clases_a and "titSpec" in clases_b:
                etiq = _limpiar_espacios(ps[i].get_text(" ", strip=True))
                val = _limpiar_espacios(ps[i + 1].get_text(" ", strip=True))
                if etiq and val:
                    pares.append((etiq, val))
                i += 2
            else:
                i += 1
    pares.extend(_pares_desde_tablas_html(soup))
    return pares


def parsear_akt_modelo_html(
    html: str,
    url_fuente: str,
    dominios: list[str] | None = None,
    hoy: str | None = None,
) -> ModeloOficial | None:
    doms = dominios or ["aktmotos.com"]
    soup = BeautifulSoup(html, "html.parser")
    nombre = ""
    h1 = soup.find("h1")
    if h1:
        nombre = _limpiar_espacios(h1.get_text(" ", strip=True))
    if not nombre and soup.title:
        titulo = _limpiar_espacios(soup.title.get_text(" ", strip=True))
        nombre = re.sub(r"\s*[-|]\s*AKT\s*Motos.*$", "", titulo, flags=re.IGNORECASE).strip()
    if not nombre or _es_no_motocicleta(nombre, url_fuente):
        return None

    texto = soup.get_text(" ", strip=True)
    cc = None
    for pat in (_RE_ANTES_CILINDRAJE, _RE_DESPUES_CILINDRAJE):
        for m in pat.finditer(texto):
            cc = parsear_cc(f"{m.group(1)} cc")
            if cc is not None:
                break
        if cc is not None:
            break
    if cc is None:
        return None

    partes = [p for p in urlparse(url_fuente).path.strip("/").split("/") if p]
    categoria = partes[1].replace("-", " ").title() if len(partes) >= 3 else None

    url_manual = None
    for a in soup.find_all("a", href=True):
        etiqueta = _limpiar_espacios(a.get_text(" ", strip=True)).lower()
        href = _absolutizar(a["href"], url_fuente, doms)
        if not href:
            continue
        if ".pdf" in href.lower() and ("manual" in etiqueta or "manual" in href.lower()):
            url_manual = href
            break

    hoy_str = hoy or datetime.date.today().isoformat()
    pares = _akt_pares_desde_soup(soup)
    ficha = construir_ficha_tecnica(pares, url_fuente, texto, hoy_str) if pares else {}

    return ModeloOficial(
        modelo=nombre,
        cilindraje_cc=cc,
        categoria=categoria,
        url_fuente=url_fuente,
        url_manual=url_manual,
        ficha_tecnica=ficha or None,
    )


# ---------------------------------------------------------------------------
# Orquestación de descarga por marca
# ---------------------------------------------------------------------------

class AdaptadorBase:
    def __init__(
        self,
        marca: str,
        config: dict[str, Any],
        client: OficialHttpClient,
        hoy: str | None = None,
        incluir_fichas: bool = True,
    ) -> None:
        self.marca = marca
        self.config = config
        self.dominios: list[str] = list(config.get("dominios", []))
        self.client = client
        self.hoy = hoy or datetime.date.today().isoformat()
        self.incluir_fichas = incluir_fichas

    def recolectar(self) -> list[ModeloOficial]:
        raise NotImplementedError


class AdaptadorYamaha(AdaptadorBase):
    def recolectar(self) -> list[ModeloOficial]:
        urls: list[str] = []
        vistos: set[str] = set()
        for fuente in self.config.get("catalogo", []):
            try:
                contenido = self.client.get_text(fuente)
            except Exception as e:
                logger.warning("Yamaha: error al leer %s: %s", fuente, e)
                continue
            extraidas = (
                parsear_yamaha_sitemap(contenido, self.dominios)
                if fuente.endswith(".xml")
                else parsear_yamaha_catalogo_links(contenido, fuente, self.dominios)
            )
            for u in extraidas:
                if u not in vistos:
                    vistos.add(u)
                    urls.append(u)

        modelos: list[ModeloOficial] = []
        claves_vistas: set[str] = set()
        for url in urls:
            modelo = None
            post_id = _yamaha_extraer_post_id(url)
            if post_id:
                api_url = f"https://www.incolmotos-yamaha.com.co/wp-json/v2/vehicules/{post_id}/"
                try:
                    raw_api = self.client.get_text(api_url, headers={"Rest-Api-Key": YAMAHA_REST_API_KEY})
                    modelo = parsear_yamaha_vehiculo_api(json.loads(raw_api), url, self.dominios, hoy=self.hoy)
                except Exception as e:
                    logger.debug("Yamaha API fallback HTML para %s: %s", url, e)
            if modelo is None:
                try:
                    html = self.client.get_text(url)
                    modelo = parsear_yamaha_vehiculo_html(html, url, self.dominios, hoy=self.hoy)
                except Exception as e:
                    logger.warning("Yamaha: no se pudo procesar %s: %s", url, e)
                    continue
            if modelo is None:
                logger.info("Yamaha: omitido %s (sin cilindrada explícita o no es moto)", url)
                continue
            clave = f"{modelo.modelo.lower()}|{modelo.cilindraje_cc}"
            if clave not in claves_vistas:
                claves_vistas.add(clave)
                modelos.append(modelo)
        return modelos


class AdaptadorHonda(AdaptadorBase):
    def recolectar(self) -> list[ModeloOficial]:
        modelos: list[ModeloOficial] = []
        claves_vistas: set[str] = set()
        for fuente in self.config.get("catalogo", []):
            try:
                html = self.client.get_rendered_html(fuente, wait_ms=4000)
            except Exception as e:
                logger.warning("Honda: error al leer %s: %s", fuente, e)
                continue
            for m in parsear_honda_catalogo_html(html, fuente, self.dominios):
                clave = f"{m.modelo.lower()}|{m.cilindraje_cc}"
                if clave not in claves_vistas:
                    claves_vistas.add(clave)
                    modelos.append(m)

        if not self.incluir_fichas:
            return modelos

        con_fichas: list[ModeloOficial] = []
        for m in modelos:
            try:
                pdp_html = self.client.get_rendered_html(m.url_fuente, wait_ms=3000)
                ficha = parsear_honda_modelo_html(pdp_html, m.url_fuente, hoy=self.hoy)
                if not ficha:
                    pdp_html = self.client.get_rendered_html(m.url_fuente, wait_ms=5000)
                    ficha = parsear_honda_modelo_html(pdp_html, m.url_fuente, hoy=self.hoy)
                con_fichas.append(replace(m, ficha_tecnica=ficha or None))
            except Exception as e:
                logger.warning("Honda: no se pudo extraer ficha de %s: %s", m.url_fuente, e)
                con_fichas.append(m)
        return con_fichas


class AdaptadorBajaj(AdaptadorBase):
    def recolectar(self) -> list[ModeloOficial]:
        urls: list[str] = []
        vistos: set[str] = set()
        for fuente in self.config.get("catalogo", []):
            try:
                html = self.client.get_rendered_html(fuente, wait_ms=3000)
            except Exception as e:
                logger.warning("Bajaj: error al leer %s: %s", fuente, e)
                continue
            for u in parsear_bajaj_catalogo_links(html, fuente, self.dominios):
                if u not in vistos:
                    vistos.add(u)
                    urls.append(u)

        modelos: list[ModeloOficial] = []
        claves_vistas: set[str] = set()
        for url in urls:
            try:
                html = self.client.get_rendered_html(url, wait_ms=2000)
            except Exception as e:
                logger.warning("Bajaj: error en %s: %s", url, e)
                continue
            modelo = parsear_bajaj_modelo_html(html, url, self.dominios, hoy=self.hoy)
            if modelo is None:
                logger.info("Bajaj: omitido %s (sin cilindrada explícita o no es moto)", url)
                continue
            clave = f"{modelo.modelo.lower()}|{modelo.cilindraje_cc}"
            if clave not in claves_vistas:
                claves_vistas.add(clave)
                modelos.append(modelo)
        return modelos


class AdaptadorAkt(AdaptadorBase):
    def recolectar(self) -> list[ModeloOficial]:
        urls: list[str] = []
        vistos: set[str] = set()
        for fuente in self.config.get("catalogo", []):
            try:
                html = self.client.get_rendered_html(fuente, wait_ms=3000)
            except Exception as e:
                logger.warning("AKT: error al leer %s: %s", fuente, e)
                continue
            for u in parsear_akt_catalogo_links(html, fuente, self.dominios):
                if u not in vistos:
                    vistos.add(u)
                    urls.append(u)

        modelos: list[ModeloOficial] = []
        claves_vistas: set[str] = set()
        for url in urls:
            try:
                html = self.client.get_rendered_html(url, wait_ms=2000)
            except Exception as e:
                logger.warning("AKT: error en %s: %s", url, e)
                continue
            modelo = parsear_akt_modelo_html(html, url, self.dominios, hoy=self.hoy)
            if modelo is None:
                logger.info("AKT: omitido %s (sin cilindrada explícita o no es moto)", url)
                continue
            clave = f"{modelo.modelo.lower()}|{modelo.cilindraje_cc}"
            if clave not in claves_vistas:
                claves_vistas.add(clave)
                modelos.append(modelo)
        return modelos


ADAPTADORES: dict[str, type[AdaptadorBase]] = {
    "yamaha": AdaptadorYamaha,
    "honda": AdaptadorHonda,
    "bajaj": AdaptadorBajaj,
    "akt": AdaptadorAkt,
}
