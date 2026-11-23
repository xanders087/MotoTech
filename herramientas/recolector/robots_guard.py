#!/usr/bin/env python3
"""Seguridad de fuentes oficiales, respeto a robots.txt y límite de frecuencia."""
from __future__ import annotations

import logging
import time
import urllib.error
import urllib.request
import urllib.robotparser
from typing import Callable
from urllib.parse import urlparse

logger = logging.getLogger("recolector")

USER_AGENT = "MotoTechBot/1.0 (+https://github.com/mototech)"
BROWSER_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)


def _normalizar_dominio(dom: str) -> str:
    clean = dom.strip().lower().rstrip(".")
    return clean.removeprefix("www.")


def es_url_oficial(url: str, dominios_permitidos: list[str]) -> bool:
    """True solo si la URL es HTTPS y pertenece a un dominio de la lista blanca."""
    if not isinstance(url, str) or not url.strip():
        return False
    try:
        parsed = urlparse(url.strip())
    except ValueError:
        return False
    if parsed.scheme.lower() != "https" or not parsed.hostname:
        return False
    host = _normalizar_dominio(parsed.hostname)
    if not host or "." not in host:
        return False
    for permitido in dominios_permitidos:
        dom = _normalizar_dominio(permitido)
        if not dom:
            continue
        if host == dom or host.endswith(f".{dom}"):
            return True
    return False


class RobotsGuard:
    """Cachea y valida reglas de robots.txt por dominio."""

    def __init__(
        self,
        fetcher: Callable[[str], tuple[int, str]] | None = None,
        user_agent: str = USER_AGENT,
        fallback_fetcher: Callable[[str], tuple[int, str]] | None = None,
    ) -> None:
        self._fetcher = fetcher or self._default_fetch
        if fallback_fetcher is not None:
            self._fallback_fetcher: Callable[[str], tuple[int, str]] | None = fallback_fetcher
        elif fetcher is None:
            self._fallback_fetcher = self._playwright_fetch
        else:
            self._fallback_fetcher = None
        self._user_agent = user_agent
        self._cache: dict[str, urllib.robotparser.RobotFileParser | None] = {}

    @staticmethod
    def _default_fetch(robots_url: str) -> tuple[int, str]:
        req = urllib.request.Request(
            robots_url,
            headers={"User-Agent": USER_AGENT, "Accept": "text/plain,*/*"},
        )
        try:
            with urllib.request.urlopen(req, timeout=12) as resp:
                body = resp.read().decode("utf-8", "replace")
                return resp.status, body
        except urllib.error.HTTPError as e:
            return e.code, ""
        except Exception as e:
            logger.warning("No se pudo leer %s: %s", robots_url, e)
            return 0, ""

    @staticmethod
    def _playwright_fetch(robots_url: str) -> tuple[int, str]:
        try:
            from playwright.sync_api import sync_playwright
        except ImportError:
            return 403, ""

        try:
            with sync_playwright() as p:
                browser = p.chromium.launch(
                    headless=True,
                    args=["--disable-blink-features=AutomationControlled"],
                )
                try:
                    ctx = browser.new_context(user_agent=BROWSER_USER_AGENT, locale="es-CO")
                    try:
                        try:
                            import playwright_stealth

                            playwright_stealth.Stealth().apply_stealth_sync(ctx)
                        except ImportError:
                            pass
                        page = ctx.new_page()
                        resp = page.goto(robots_url, wait_until="domcontentloaded", timeout=15000)
                        if resp is None:
                            return 403, ""
                        status = resp.status
                        if status != 200:
                            return status, ""
                        ctype = (resp.headers.get("content-type") or "").lower()
                        if "text/html" in ctype:
                            return 403, ""
                        try:
                            body = resp.text()
                        except Exception:
                            body = page.inner_text("body")
                        if body.lstrip().lower().startswith(("<!doctype html", "<html")):
                            return 403, ""
                        return status, body
                    finally:
                        ctx.close()
                finally:
                    browser.close()
        except Exception as e:
            logger.warning("Fallback Playwright falló al leer %s: %s", robots_url, e)
            return 403, ""

    def _parser_para_host(self, scheme: str, netloc: str) -> urllib.robotparser.RobotFileParser | None:
        key = f"{scheme}://{netloc}".lower()
        if key in self._cache:
            return self._cache[key]

        robots_url = f"{scheme}://{netloc}/robots.txt"
        status, body = self._fetcher(robots_url)
        if status in (401, 403) and self._fallback_fetcher is not None:
            logger.info("HTTP %d al leer %s; reintentando con fallback de navegador", status, robots_url)
            status, body = self._fallback_fetcher(robots_url)
        rp = urllib.robotparser.RobotFileParser()
        rp.set_url(robots_url)
        if status == 200:
            rp.parse(body.splitlines())
            self._cache[key] = rp
        elif status in (404, 410):
            # Sin robots.txt: permitido por estándar.
            rp.parse([])
            self._cache[key] = rp
        elif status in (401, 403):
            # Bloqueo explícito al robots.txt: disallow total por precaución.
            rp.disallow_all = True
            self._cache[key] = rp
        else:
            # Error de red temporal: conservador -> None (no permite hasta confirmar).
            self._cache[key] = None
        return self._cache[key]

    def puede_consultar(self, url: str) -> bool:
        parsed = urlparse(url)
        if parsed.scheme.lower() != "https" or not parsed.netloc:
            return False
        rp = self._parser_para_host(parsed.scheme, parsed.netloc)
        if rp is None:
            return False
        return rp.can_fetch(self._user_agent, url) and rp.can_fetch("*", url)


class RateLimiter:
    """Limita la frecuencia de peticiones por host para no sobrecargar los sitios."""

    def __init__(self, delay_segundos: float = 0.8) -> None:
        self.delay_segundos = max(0.0, delay_segundos)
        self._ultimo_por_host: dict[str, float] = {}

    def esperar(self, url: str) -> None:
        if self.delay_segundos <= 0:
            return
        host = (urlparse(url).hostname or "").lower()
        ahora = time.monotonic()
        ultimo = self._ultimo_por_host.get(host)
        if ultimo is not None:
            restante = self.delay_segundos - (ahora - ultimo)
            if restante > 0:
                time.sleep(restante)
        self._ultimo_por_host[host] = time.monotonic()


class OficialHttpClient:
    """Cliente HTTP/Playwright que valida lista blanca, robots.txt y rate limit."""

    def __init__(
        self,
        dominios_permitidos: list[str],
        robots_guard: RobotsGuard | None = None,
        rate_limiter: RateLimiter | None = None,
        timeout_seg: int = 25,
    ) -> None:
        self.dominios_permitidos = dominios_permitidos
        self.robots = robots_guard or RobotsGuard()
        self.limiter = rate_limiter or RateLimiter()
        self.timeout_seg = timeout_seg
        self._pw = None
        self._browser = None
        self._context = None

    def _validar_acceso(self, url: str) -> None:
        if not es_url_oficial(url, self.dominios_permitidos):
            raise ValueError(f"URL fuera de lista blanca o sin HTTPS: {url}")
        if not self.robots.puede_consultar(url):
            raise PermissionError(f"robots.txt prohíbe consultar: {url}")

    def get_text(self, url: str, headers: dict[str, str] | None = None, reintentos_429: int = 2) -> str:
        self._validar_acceso(url)
        req_headers = {"User-Agent": USER_AGENT, "Accept-Language": "es-CO,es;q=0.9"}
        if headers:
            req_headers.update(headers)
        for intento in range(reintentos_429 + 1):
            self.limiter.esperar(url)
            req = urllib.request.Request(url, headers=req_headers)
            try:
                with urllib.request.urlopen(req, timeout=self.timeout_seg) as resp:
                    final_url = resp.geturl()
                    if not es_url_oficial(final_url, self.dominios_permitidos):
                        raise ValueError(f"Redirección fuera de lista blanca: {final_url}")
                    return resp.read().decode("utf-8", "replace")
            except urllib.error.HTTPError as e:
                if e.code in (429, 503) and intento < reintentos_429:
                    espera = 4 * (intento + 1)
                    logger.info("HTTP %d en %s; esperando %ds antes de reintentar", e.code, url, espera)
                    time.sleep(espera)
                    continue
                raise
        raise RuntimeError(f"HTTP 429 persistente al cargar {url}")

    def get_rendered_html(self, url: str, wait_ms: int = 2500, reintentos_429: int = 2) -> str:
        self._validar_acceso(url)
        if self._browser is None:
            from playwright.sync_api import sync_playwright

            self._pw = sync_playwright().start()
            self._browser = self._pw.chromium.launch(
                headless=True,
                args=["--disable-blink-features=AutomationControlled"],
            )
        for intento in range(reintentos_429 + 1):
            self.limiter.esperar(url)
            ctx = self._browser.new_context(
                user_agent=BROWSER_USER_AGENT,
                locale="es-CO",
            )
            try:
                try:
                    import playwright_stealth

                    playwright_stealth.Stealth().apply_stealth_sync(ctx)
                except ImportError:
                    pass
                page = ctx.new_page()
                resp = page.goto(url, wait_until="domcontentloaded", timeout=self.timeout_seg * 1000)
                if wait_ms > 0:
                    page.wait_for_timeout(wait_ms)
                final_url = page.url
                if final_url and not es_url_oficial(final_url, self.dominios_permitidos):
                    raise ValueError(f"Redirección fuera de lista blanca: {final_url}")
                status = resp.status if resp is not None else 200
                if status == 429 and intento < reintentos_429:
                    espera = 6 * (intento + 1)
                    logger.info("HTTP 429 en %s; esperando %ds antes de reintentar", url, espera)
                    time.sleep(espera)
                    continue
                if status >= 400:
                    raise RuntimeError(f"HTTP {status} al cargar {url}")
                return page.content()
            finally:
                ctx.close()
        raise RuntimeError(f"HTTP 429 persistente al cargar {url}")

    def close(self) -> None:
        if self._context is not None:
            self._context.close()
            self._context = None
        if self._browser is not None:
            self._browser.close()
            self._browser = None
        if self._pw is not None:
            self._pw.stop()
            self._pw = None

    def __enter__(self) -> "OficialHttpClient":
        return self

    def __exit__(self, *args) -> None:
        self.close()
