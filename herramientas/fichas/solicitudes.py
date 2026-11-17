#!/usr/bin/env python3
"""Lista las fichas que más piden los usuarios (colección "solicitudes" de Firestore).

Requiere:  pip install firebase-admin
           y GOOGLE_APPLICATION_CREDENTIALS con la clave de la cuenta de servicio.
Uso:  python3 herramientas/fichas/solicitudes.py [cuántas]
"""
from __future__ import annotations

import sys


def formatear(solicitudes: list[dict], limite: int = 30) -> str:
    """Tabla de solicitudes, de la más pedida a la menos pedida."""
    orden = sorted(solicitudes, key=lambda s: (-int(s.get("total") or 0), str(s.get("clave") or "")))
    if not orden:
        return "No hay solicitudes."
    lineas = [f"{'Pedidos':>7}  Moto"]
    for s in orden[:limite]:
        moto = "{} {} {}cc {}".format(s.get("marca", "?"), s.get("modelo", "?"), s.get("cilindraje_cc", "?"), s.get("anio", "?"))
        lineas.append(f"{int(s.get('total') or 0):>7}  {moto}")
    if len(orden) > limite:
        lineas.append(f"… y {len(orden) - limite} más.")
    return "\n".join(lineas)


def main(argv: list[str]) -> int:
    limite = int(argv[1]) if len(argv) > 1 else 30
    import os
    import firebase_admin
    from firebase_admin import firestore

    firebase_admin.initialize_app()
    db_id = os.environ.get(
        "FIRESTORE_DATABASE_ID",
        "ai-studio-android-mototech-364d3daf-8123-4de3-b899-775d66bdcdfb",
    )
    docs = firestore.client(database_id=db_id).collection("solicitudes").stream()
    print(formatear([d.to_dict() for d in docs], limite))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
