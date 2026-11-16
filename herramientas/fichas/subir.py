#!/usr/bin/env python3
"""Publica en Firestore las fichas válidas de la carpeta fichas/.

Requiere:  pip install firebase-admin
           y la variable GOOGLE_APPLICATION_CREDENTIALS con la ruta de la clave de la
           cuenta de servicio del proyecto de Firebase.
Uso:  python3 herramientas/fichas/subir.py [--simular] [carpeta]

No sube nada si alguna ficha tiene errores. No borra fichas de Firestore: para retirar una,
se elimina a mano en la consola.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validar  # noqa: E402

COLECCION = "fichas"


def documento(ficha: dict) -> dict:
    """La ficha tal como la lee la app, con los campos por los que se busca."""
    return {
        **ficha,
        "clave": validar.clave(ficha["marca"], ficha["modelo"], ficha["cilindraje_cc"]),
        "estado": "publicada",
    }


def main(argv: list[str]) -> int:
    args = [a for a in argv[1:] if a != "--simular"]
    simular = "--simular" in argv
    carpeta = Path(args[0]) if args else Path(__file__).resolve().parents[2] / "fichas"

    errores = validar.validar_carpeta(carpeta)
    if errores:
        print("Hay fichas con errores; no se sube nada. Ejecuta validar.py para verlos.")
        return 1
    fichas, _ = validar.cargar_fichas(carpeta)
    if not fichas:
        print("No hay fichas para subir.")
        return 0

    db = None
    if not simular:
        import firebase_admin
        from firebase_admin import firestore

        firebase_admin.initialize_app()
        db = firestore.client()

    for ficha in fichas.values():
        doc_id = validar.id_ficha(ficha)
        if db is None:
            print(f"(simulación) {COLECCION}/{doc_id}")
            continue
        datos = documento(ficha)
        datos["actualizado"] = firestore.SERVER_TIMESTAMP
        db.collection(COLECCION).document(doc_id).set(datos)
        print(f"publicada {COLECCION}/{doc_id}")
    print(f"{len(fichas)} fichas {'simuladas' if simular else 'publicadas'}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
