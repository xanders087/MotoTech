#!/usr/bin/env python3
"""Adaptadores oficiales por marca para el recolector de catálogo y manuales.

Principio: el cilindraje nunca se infiere del nombre del modelo; siempre se extrae
de una etiqueta explícita de ficha técnica o se descarta si no está presente.
"""
from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Any
from urllib.parse import urljoin, urlparse

from bs4 import BeautifulSoup

from robots_guard import OficialHttpClient, es_url_oficial

logger = logging.getLogger("recolector")

YAMAHA_REST_API_KEY = "5434ad6c-605c-4fe9-b73f-d5c085e35da8"

_EXCLUIR_NO_MOTO = re.compile(
    r"\b(cuatrimotos?|atvs?|sxs|side[\s-]*by[\s-]*side|utvs?|motocarros?|cargueros?|kargueros?|tuk[\s-]*tuk|torito|maxima|qute|pioneer|talon|trx\w*|yfm\w*|kodiak|wolverine|grizzly|viking)\b",
    re.IGNORECASE,
)

_RE_CC_VALOR = re.compile(
    r"(\d{2,4}(?:[.,]\d{1,2})?)\s*(?:cc|c\.c\.|cm3|cm³)\b",
    re.IGNORECASE,
)
_RE_DESPUES_CILINDRAJE = re.compile(
    r"cilindraj\w*\s*[:\-]?\s*(\d{2,4}(?:[.,]\d{1,2})?)\s*(?:cc|c\.c\.|cm3|cm³)?",
    re.IGNORECASE,
)
_RE_ANTES_CILINDRAJE = re.compile(
    r"(\d{2,4}(?:[.,]\d{1,2})?)\s*(?:cc|c\.c\.|cm3|cm³)\s*cilindraj\w*",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class ModeloOficial:
    modelo: str
    cilindraje_cc: int
    categoria: str | None
    url_fuente: str
    url_manual: str | None = None


def parsear_cc(texto: str | None) -> int | None:
    """Extrae un entero en cc desde una cadena explícita de cilindrada (p. ej. '109,19 cm³')."""
    if not isinstance(texto, str) or not texto.strip():
        return None
    m = _RE_CC_VALOR.search(texto)
    if not m:
        m = re.search(r"^\s*(\d{2,4}(?:[.,]\d{1,2})?)\s*$", texto)
    if not m:
        return None
    try:
        valor = int(float(m.group(1).replace(",", ".")))
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


def parsear_yamaha_vehiculo_api(
    data: dict[str, Any],
    url_fuente: str,
    dominios: list[str] | None = None,
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

    if cc is None:
        # Buscar en bloques de resumen/especificaciones dentro de detail
        for clave_tabla in ("resumen_especificaciones", "especificaciones_completas", "tabla_resumen_especificaciones"):
            bloque = detail.get(clave_tabla)
            if isinstance(bloque, list):
                for fila in bloque:
                    if not isinstance(fila, dict):
                        continue
                    blob = " ".join(str(v) for v in fila.values() if v is not None)
                    if "cilindraj" in blob.lower():
                        cc = parsear_cc(blob)
                        if cc is not None:
                            break
            if cc is not None:
                break

    if cc is None:
        return None

    return ModeloOficial(
        modelo=nombre,
        cilindraje_cc=cc,
        categoria=categoria,
        url_fuente=url_fuente,
        url_manual=_yamaha_manual_desde_detail(detail, doms),
    )


def parsear_yamaha_vehiculo_html(
    html: str,
    url_fuente: str,
    dominios: list[str] | None = None,
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

    return ModeloOficial(
        modelo=nombre,
        cilindraje_cc=cc,
        categoria=categoria,
        url_fuente=url_fuente,
        url_manual=url_manual,
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
        # Extraer el argumento string de self.__next_f.push([1,"..."])
        for m in re.finditer(r'self\.__next_f\.push\(\[\s*\d+\s*,\s*("(?:\\.|[^"\\])*")\s*\]\)', raw):
            try:
                payload = json.loads(m.group(1))
            except json.JSONDecodeError:
                continue
            # Buscar sub-objetos JSON dentro del payload Flight
            for idx_m in re.finditer(r'\{"[^"]+"\s*:', payload):
                start = idx_m.start()
                if "pdpUrl" not in payload[start : start + 800]:
                    continue
                try:
                    parsed, _ = json.JSONDecoder().raw_decode(payload[start:])
                    candidatos.extend(_iter_dicts_con_pdp(parsed))
                except json.JSONDecodeError:
                    continue

        # Soporte adicional si hay JSON directo en <script type="application/json">
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
        # Esperado: ["colombia", "motos", "<categoria>", "<modelo>"]
        if len(partes) < 4:
            continue
        if _es_no_motocicleta(u, a.get_text(" ", strip=True)):
            continue
        norm = u.rstrip("/") + "/"
        if norm not in vistos:
            vistos.add(norm)
            resultado.append(norm)
    return resultado


def parsear_bajaj_modelo_html(
    html: str,
    url_fuente: str,
    dominios: list[str] | None = None,
) -> ModeloOficial | None:
    doms = dominios or ["grupouma.com", "colombia.globalbajaj.com"]
    soup = BeautifulSoup(html, "html.parser")
    h1 = soup.find("h1")
    nombre = _limpiar_espacios(h1.get_text(" ", strip=True)) if h1 else ""
    if not nombre and soup.title:
        nombre = _limpiar_espacios(soup.title.get_text(" ", strip=True).split("|")[0].split("-")[0])
    if not nombre or _es_no_motocicleta(nombre, url_fuente):
        return None

    texto = soup.get_text(" ", strip=True)
    # Bajaj incluye una tabla comparativa con motos de la competencia después de esta frase.
    corte = texto.lower().find("descubre por qué somos mejores")
    texto_propio = texto[:corte] if corte > 0 else texto

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
    for a in soup.find_all("a", href=True):
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

    return ModeloOficial(
        modelo=nombre,
        cilindraje_cc=cc,
        categoria=categoria,
        url_fuente=url_fuente,
        url_manual=url_manual,
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
        # Esperado: ["motos-akt", "<categoria>", "<modelo>"]
        if len(partes) < 3:
            continue
        if _es_no_motocicleta(u, a.get_text(" ", strip=True)):
            continue
        norm = u.rstrip("/") + "/"
        if norm not in vistos:
            vistos.add(norm)
            resultado.append(norm)
    return resultado


def parsear_akt_modelo_html(
    html: str,
    url_fuente: str,
    dominios: list[str] | None = None,
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

    return ModeloOficial(
        modelo=nombre,
        cilindraje_cc=cc,
        categoria=categoria,
        url_fuente=url_fuente,
        url_manual=url_manual,
    )


# ---------------------------------------------------------------------------
# Orquestación de descarga por marca
# ---------------------------------------------------------------------------

class AdaptadorBase:
    def __init__(self, marca: str, config: dict[str, Any], client: OficialHttpClient) -> None:
        self.marca = marca
        self.config = config
        self.dominios: list[str] = list(config.get("dominios", []))
        self.client = client

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
                    modelo = parsear_yamaha_vehiculo_api(json.loads(raw_api), url, self.dominios)
                except Exception as e:
                    logger.debug("Yamaha API fallback HTML para %s: %s", url, e)
            if modelo is None:
                try:
                    html = self.client.get_text(url)
                    modelo = parsear_yamaha_vehiculo_html(html, url, self.dominios)
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
        return modelos


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
            modelo = parsear_bajaj_modelo_html(html, url, self.dominios)
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
            modelo = parsear_akt_modelo_html(html, url, self.dominios)
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
