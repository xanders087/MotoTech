#!/usr/bin/env python3
"""Recolector semanal de catálogo oficial de motocicletas.

Uso:
  python herramientas/recolector/recolectar.py [--simular] [--sin-snapshot] [--marca Yamaha]

- Lee herramientas/recolector/fuentes_oficiales.json
- Valida cada URL con lista blanca HTTPS + robots.txt + rate limit
- Conserva modelos descontinuados como vigente=false
- Nunca borra una marca si el adaptador devuelve 0 modelos (mantiene snapshot previo y alerta)
- Cruza tiene_ficha con las fichas válidas de fichas/
- Actualiza app/src/main/resources/motorcycle_catalog.json y publica catalogo/{marca} en Firestore
"""
from __future__ import annotations

import argparse
import datetime
import json
import logging
import os
import sys
from pathlib import Path
from typing import Any

RAIZ_REPO = Path(__file__).resolve().parents[2]
CARPETA_RECOLECTOR = Path(__file__).resolve().parent
CARPETA_FICHAS_TOOLS = RAIZ_REPO / "herramientas" / "fichas"

for p in (str(CARPETA_RECOLECTOR), str(CARPETA_FICHAS_TOOLS)):
    if p not in sys.path:
        sys.path.insert(0, p)

import validar  # noqa: E402
from adaptadores import ADAPTADORES, ModeloOficial  # noqa: E402
from robots_guard import OficialHttpClient, RobotsGuard  # noqa: E402

logger = logging.getLogger("recolector")

COLECCION_CATALOGO = "catalogo"
RUTA_FUENTES_DEFECTO = CARPETA_RECOLECTOR / "fuentes_oficiales.json"
RUTA_SNAPSHOT_DEFECTO = RAIZ_REPO / "app" / "src" / "main" / "resources" / "motorcycle_catalog.json"
RUTA_FICHAS_DEFECTO = RAIZ_REPO / "fichas"


def claves_con_ficha(carpeta_fichas: Path) -> set[str]:
    """Devuelve el conjunto de claves marca|modelo|cc que tienen al menos una ficha válida."""
    if not carpeta_fichas.is_dir():
        return set()
    fichas, _ = validar.cargar_fichas(carpeta_fichas)
    claves: set[str] = set()
    for ficha in fichas.values():
        if not validar.validar_ficha(ficha):
            claves.add(validar.clave(ficha["marca"], ficha["modelo"], ficha["cilindraje_cc"]))
    return claves


def _parsear_cc_snapshot(valor: Any) -> int | None:
    if isinstance(valor, int) and not isinstance(valor, bool):
        return valor if 40 <= valor <= 2600 else None
    if isinstance(valor, str):
        digitos = "".join(c for c in valor if c.isdigit())
        if digitos:
            num = int(digitos)
            return num if 40 <= num <= 2600 else None
    return None


def combinar_modelos(
    marca: str,
    nuevos: list[ModeloOficial],
    previos_snapshot: list[dict[str, Any]],
    claves_fichas: set[str],
    hoy: str,
) -> list[dict[str, Any]]:
    """Combina modelos recién recolectados con el catálogo previo sin perder descontinuados."""
    combinados: dict[tuple[str, int], dict[str, Any]] = {}

    for prev in previos_snapshot:
        if not isinstance(prev, dict):
            continue
        modelo_prev = str(prev.get("modelo") or "").strip()
        cc_prev = _parsear_cc_snapshot(prev.get("cilindraje_cc") or prev.get("cilindraje"))
        if not modelo_prev or cc_prev is None:
            continue
        clave_item = (validar.normalizar(modelo_prev), cc_prev)
        clave_ficha = validar.clave(marca, modelo_prev, cc_prev)
        prev_cat = prev.get("categoria")
        if isinstance(prev_cat, str) and prev_cat.strip().lower() in ("cbu", "ckd"):
            prev_cat = None
        combinados[clave_item] = {
            "modelo": modelo_prev,
            "cilindraje_cc": cc_prev,
            "categoria": prev_cat,
            "url_fuente": prev.get("url_fuente"),
            "url_manual": prev.get("url_manual"),
            "tiene_ficha": clave_ficha in claves_fichas,
            "vigente": False,
            "visto_por_ultima_vez": prev.get("visto_por_ultima_vez") or hoy,
        }

    for nuevo in nuevos:
        clave_item = (validar.normalizar(nuevo.modelo), nuevo.cilindraje_cc)
        clave_ficha = validar.clave(marca, nuevo.modelo, nuevo.cilindraje_cc)
        prev = combinados.get(clave_item, {})
        combinados[clave_item] = {
            "modelo": nuevo.modelo,
            "cilindraje_cc": nuevo.cilindraje_cc,
            "categoria": nuevo.categoria or prev.get("categoria"),
            "url_fuente": nuevo.url_fuente,
            "url_manual": nuevo.url_manual or prev.get("url_manual"),
            "tiene_ficha": clave_ficha in claves_fichas,
            "vigente": True,
            "visto_por_ultima_vez": hoy,
        }

    return sorted(combinados.values(), key=lambda x: (not x["vigente"], x["cilindraje_cc"], x["modelo"].lower()))


def a_modelo_snapshot(item: dict[str, Any]) -> dict[str, Any]:
    """Convierte un registro de catálogo al formato JSON de motorcycle_catalog.json."""
    out: dict[str, Any] = {
        "modelo": item["modelo"],
        "cilindraje": f"{item['cilindraje_cc']}cc",
    }
    if item.get("categoria"):
        out["categoria"] = item["categoria"]
    if item.get("url_fuente"):
        out["url_fuente"] = item["url_fuente"]
    out["tiene_ficha"] = bool(item.get("tiene_ficha", False))
    out["vigente"] = bool(item.get("vigente", True))
    if item.get("visto_por_ultima_vez"):
        out["visto_por_ultima_vez"] = item["visto_por_ultima_vez"]
    return out


def documento_catalogo(marca: str, config: dict[str, Any], modelos: list[dict[str, Any]], hoy: str) -> dict[str, Any]:
    """Estructura del documento Firestore en catalogo/{marcaSlug}."""
    return {
        "marca": marca,
        "fuentes": list(config.get("catalogo", [])),
        "modelos": modelos,
        "actualizado_en": hoy,
        "estado": "publicada",
    }


def ejecutar_recoleccion(
    ruta_fuentes: Path = RUTA_FUENTES_DEFECTO,
    ruta_snapshot: Path = RUTA_SNAPSHOT_DEFECTO,
    ruta_fichas: Path = RUTA_FICHAS_DEFECTO,
    marcas_filtro: list[str] | None = None,
    simular: bool = True,
    escribir_snapshot: bool = True,
    hoy: str | None = None,
    adaptadores_registry: dict[str, Any] | None = None,
    robots_guard: RobotsGuard | None = None,
) -> tuple[dict[str, dict[str, Any]], list[str]]:
    """Ejecuta la recolección de las marcas configuradas y devuelve (docs_firestore, alertas)."""
    hoy_str = hoy or datetime.date.today().isoformat()
    fuentes = json.loads(ruta_fuentes.read_text(encoding="utf-8"))
    snapshot_actual: dict[str, list[dict[str, Any]]] = (
        json.loads(ruta_snapshot.read_text(encoding="utf-8"))
        if ruta_snapshot.is_file()
        else {}
    )
    fichas_claves = claves_con_ficha(ruta_fichas)
    registry = adaptadores_registry or ADAPTADORES
    guard = robots_guard or RobotsGuard()

    filtro_norm = {m.strip().lower() for m in marcas_filtro} if marcas_filtro else None
    docs_publicar: dict[str, dict[str, Any]] = {}
    alertas: list[str] = []

    for marca, cfg in fuentes.items():
        if filtro_norm and marca.lower() not in filtro_norm:
            continue
        nombre_adaptador = cfg.get("adaptador")
        cls_adaptador = registry.get(nombre_adaptador)
        if cls_adaptador is None:
            alertas.append(f"{marca}: adaptador desconocido '{nombre_adaptador}'")
            continue

        dominios = list(cfg.get("dominios", []))
        modelos_nuevos: list[ModeloOficial] = []
        try:
            with OficialHttpClient(dominios_permitidos=dominios, robots_guard=guard) as client:
                adaptador = cls_adaptador(marca, cfg, client)
                modelos_nuevos = adaptador.recolectar()
        except Exception as e:
            alertas.append(f"{marca}: error del recolector ({e}); se conserva catálogo anterior")
            logger.exception("Error recolectando %s", marca)
            continue

        if not modelos_nuevos:
            alerta = f"{marca}: el sitio devolvió 0 modelos; se conserva el catálogo anterior"
            alertas.append(alerta)
            logger.error(alerta)
            continue

        previos = snapshot_actual.get(marca, [])
        combinados = combinar_modelos(marca, modelos_nuevos, previos, fichas_claves, hoy_str)
        snapshot_actual[marca] = [a_modelo_snapshot(item) for item in combinados]
        docs_publicar[validar._slug(marca)] = documento_catalogo(marca, cfg, combinados, hoy_str)
        vigentes = sum(1 for m in combinados if m["vigente"])
        con_manual = sum(1 for m in combinados if m.get("url_manual"))
        logger.info(
            "%s: %d vigentes, %d totales (%d con enlace de manual)",
            marca, vigentes, len(combinados), con_manual,
        )

    if escribir_snapshot and docs_publicar:
        ruta_snapshot.write_text(
            json.dumps(snapshot_actual, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    if not simular and docs_publicar:
        import firebase_admin
        from firebase_admin import firestore

        if not firebase_admin._apps:
            firebase_admin.initialize_app()
        db_id = os.environ.get(
            "FIRESTORE_DATABASE_ID",
            "ai-studio-android-mototech-364d3daf-8123-4de3-b899-775d66bdcdfb",
        )
        db = firestore.client(database_id=db_id)
        for doc_id, doc in docs_publicar.items():
            payload = {**doc, "actualizado": firestore.SERVER_TIMESTAMP}
            db.collection(COLECCION_CATALOGO).document(doc_id).set(payload)
            print(f"publicado {COLECCION_CATALOGO}/{doc_id} ({len(doc['modelos'])} modelos)")

    return docs_publicar, alertas


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description="Recolector oficial de catálogo MotoTech")
    parser.add_argument("--simular", action="store_true", help="No publica en Firestore")
    parser.add_argument("--sin-snapshot", action="store_true", help="No modifica motorcycle_catalog.json")
    parser.add_argument("--marca", action="append", help="Filtrar por marca (repetible)")
    args = parser.parse_args(argv)

    docs, alertas = ejecutar_recoleccion(
        marcas_filtro=args.marca,
        simular=args.simular,
        escribir_snapshot=not args.sin_snapshot,
    )
    for doc_id, doc in docs.items():
        vigentes = sum(1 for m in doc["modelos"] if m["vigente"])
        print(f"OK {COLECCION_CATALOGO}/{doc_id}: {vigentes} vigentes, {len(doc['modelos'])} totales")
    for alerta in alertas:
        print(f"ALERTA: {alerta}", file=sys.stderr)
    return 1 if alertas else 0


if __name__ == "__main__":
    sys.exit(main())
