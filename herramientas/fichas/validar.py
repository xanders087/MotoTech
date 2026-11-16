#!/usr/bin/env python3
"""Valida las fichas técnicas de la carpeta fichas/.

Una ficha solo es válida si cada dato indica el manual y la página de donde salió.
Uso:  python3 herramientas/fichas/validar.py [carpeta]
Sale con código 1 si alguna ficha tiene errores.
"""
from __future__ import annotations

import datetime
import json
import re
import sys
import unicodedata
from pathlib import Path

CAMPOS_FICHA = (
    "capacidad_aceite",
    "viscosidad_aceite",
    "presion_llanta_delantera",
    "presion_llanta_trasera",
    "holgura_cadena",
    "liquido_frenos",
)
CAMPOS_RAIZ = {
    "marca", "modelo", "cilindraje_cc", "anio_desde", "anio_hasta",
    "manual", "revision", "ficha_tecnica", "plan_mantenimiento",
}
CAMPOS_MANUAL = {"titulo", "edicion", "url"}
CAMPOS_REVISION = {"revisado_por", "fecha"}
CAMPOS_DATO = {"valor", "pagina"}
CAMPOS_PLAN = {"componente", "accion", "intervalo_km", "intervalo_meses", "pagina"}

# Textos de relleno que no son un dato real.
RELLENO = re.compile(r"^(todo|tbd|pendiente|por definir|n/?a|\?+|x+|-+|\.+)$", re.IGNORECASE)


def normalizar(texto: str) -> str:
    """Igual que VehicleKey.normalize en la app: minúsculas y espacios simples."""
    return re.sub(r"\s+", " ", texto.strip().lower())


def clave(marca: str, modelo: str, cilindraje_cc: int) -> str:
    """Clave con la que la app busca la ficha: marca|modelo|cilindraje."""
    return f"{normalizar(marca)}|{normalizar(modelo)}|{cilindraje_cc}"


def _slug(texto: str) -> str:
    sin_tildes = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", sin_tildes.lower()).strip("-")


def id_ficha(ficha: dict) -> str:
    """Nombre de archivo e id del documento: marca_modelo_cc_desde-hasta."""
    return "{}_{}_{}_{}-{}".format(
        _slug(ficha["marca"]), _slug(ficha["modelo"]), ficha["cilindraje_cc"],
        ficha["anio_desde"], ficha["anio_hasta"],
    )


def _es_entero(valor) -> bool:
    return isinstance(valor, int) and not isinstance(valor, bool)


def _texto_real(valor) -> bool:
    return isinstance(valor, str) and valor.strip() != "" and not RELLENO.match(valor.strip())


def _sobrantes(objeto: dict, permitidos: set, donde: str, errores: list) -> None:
    for campo in sorted(set(objeto) - permitidos):
        errores.append(f"{donde}: campo desconocido \"{campo}\" (¿error de escritura?)")


def validar_ficha(ficha, hoy: datetime.date | None = None) -> list[str]:
    """Devuelve la lista de errores de una ficha; vacía si es válida."""
    hoy = hoy or datetime.date.today()
    errores: list[str] = []
    if not isinstance(ficha, dict):
        return ["la ficha debe ser un objeto JSON"]
    _sobrantes(ficha, CAMPOS_RAIZ, "ficha", errores)

    for campo in ("marca", "modelo"):
        if not _texto_real(ficha.get(campo)):
            errores.append(f"{campo}: falta o está vacío")

    cc = ficha.get("cilindraje_cc")
    if not _es_entero(cc) or not 40 <= cc <= 2600:
        errores.append("cilindraje_cc: debe ser un número entero en cc, por ejemplo 149")

    desde, hasta = ficha.get("anio_desde"), ficha.get("anio_hasta")
    anios_ok = True
    for nombre, anio in (("anio_desde", desde), ("anio_hasta", hasta)):
        if not _es_entero(anio) or not 1950 <= anio <= hoy.year + 1:
            errores.append(f"{nombre}: debe ser un año entre 1950 y {hoy.year + 1}")
            anios_ok = False
    if anios_ok and desde > hasta:
        errores.append("anio_desde no puede ser mayor que anio_hasta")

    manual = ficha.get("manual")
    if not isinstance(manual, dict):
        errores.append("manual: falta el manual del que salen los datos")
    else:
        _sobrantes(manual, CAMPOS_MANUAL, "manual", errores)
        if not _texto_real(manual.get("titulo")):
            errores.append("manual.titulo: falta el título del manual")
        edicion = manual.get("edicion")
        if edicion is not None and not isinstance(edicion, str):
            errores.append("manual.edicion: debe ser texto o null")
        url = manual.get("url")
        if url is not None and not (isinstance(url, str) and url.startswith("https://")):
            errores.append("manual.url: debe empezar por https:// o ser null")

    revision = ficha.get("revision")
    if not isinstance(revision, dict):
        errores.append("revision: falta quién revisó la ficha y cuándo")
    else:
        _sobrantes(revision, CAMPOS_REVISION, "revision", errores)
        if not _texto_real(revision.get("revisado_por")):
            errores.append("revision.revisado_por: falta el nombre de quien cotejó la ficha con el manual")
        try:
            fecha = datetime.date.fromisoformat(revision.get("fecha") or "")
            if fecha > hoy:
                errores.append("revision.fecha: no puede ser una fecha futura")
        except (TypeError, ValueError):
            errores.append("revision.fecha: debe tener el formato AAAA-MM-DD")

    datos = 0
    tecnica = ficha.get("ficha_tecnica", {})
    if not isinstance(tecnica, dict):
        errores.append("ficha_tecnica: debe ser un objeto")
    else:
        _sobrantes(tecnica, set(CAMPOS_FICHA), "ficha_tecnica", errores)
        for campo in CAMPOS_FICHA:
            dato = tecnica.get(campo)
            if dato is None:
                continue  # El manual no trae ese dato: se deja en null, no se inventa.
            donde = f"ficha_tecnica.{campo}"
            if not isinstance(dato, dict):
                errores.append(f"{donde}: debe ser null o un objeto con valor y pagina")
                continue
            _sobrantes(dato, CAMPOS_DATO, donde, errores)
            if not _texto_real(dato.get("valor")):
                errores.append(f"{donde}.valor: falta el valor (si el manual no lo trae, usa null en todo el dato)")
            if not _es_entero(dato.get("pagina")) or dato["pagina"] < 1:
                errores.append(f"{donde}.pagina: falta la página del manual")
            datos += 1

    plan = ficha.get("plan_mantenimiento", [])
    if not isinstance(plan, list):
        errores.append("plan_mantenimiento: debe ser una lista")
        plan = []
    vistos: set[str] = set()
    for i, tarea in enumerate(plan, start=1):
        donde = f"plan_mantenimiento[{i}]"
        if not isinstance(tarea, dict):
            errores.append(f"{donde}: debe ser un objeto")
            continue
        _sobrantes(tarea, CAMPOS_PLAN, donde, errores)
        componente = tarea.get("componente")
        if not _texto_real(componente):
            errores.append(f"{donde}.componente: falta")
        else:
            nombre = normalizar(componente)
            if nombre in vistos:
                errores.append(f"{donde}.componente: \"{componente}\" está repetido")
            vistos.add(nombre)
        accion = tarea.get("accion")
        if accion is not None and not _texto_real(accion):
            errores.append(f"{donde}.accion: debe decir qué se hace (cambiar, revisar, ajustar…) o ser null")
        intervalos = 0
        for campo in ("intervalo_km", "intervalo_meses"):
            valor = tarea.get(campo)
            if valor is None:
                continue
            if not _es_entero(valor) or valor <= 0:
                errores.append(f"{donde}.{campo}: debe ser un número entero mayor que 0 o null")
            else:
                intervalos += 1
        if intervalos == 0:
            errores.append(f"{donde}: necesita intervalo_km o intervalo_meses")
        if not _es_entero(tarea.get("pagina")) or tarea["pagina"] < 1:
            errores.append(f"{donde}.pagina: falta la página del manual")
        datos += 1

    if datos == 0 and not errores:
        errores.append("la ficha no tiene ningún dato técnico ni tarea de mantenimiento")
    return errores


def cargar_fichas(carpeta: Path) -> tuple[dict[Path, dict], dict[Path, list[str]]]:
    """Lee los .json de la carpeta (los que empiezan por _ son plantillas y se ignoran)."""
    fichas: dict[Path, dict] = {}
    errores: dict[Path, list[str]] = {}
    for ruta in sorted(carpeta.glob("*.json")):
        if ruta.name.startswith("_"):
            continue
        try:
            fichas[ruta] = json.loads(ruta.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError) as e:
            errores[ruta] = [f"no es un JSON válido: {e}"]
    return fichas, errores


def validar_carpeta(carpeta: Path, hoy: datetime.date | None = None) -> dict[Path, list[str]]:
    """Valida todas las fichas y las reglas entre fichas. Devuelve solo las que tienen errores."""
    fichas, errores = cargar_fichas(carpeta)
    validas: dict[Path, dict] = {}
    for ruta, ficha in fichas.items():
        lista = validar_ficha(ficha, hoy)
        if not lista and ruta.stem != id_ficha(ficha):
            lista.append(f"el archivo debe llamarse {id_ficha(ficha)}.json")
        if lista:
            errores[ruta] = lista
        else:
            validas[ruta] = ficha

    # Dos fichas de la misma moto no pueden cubrir el mismo año: la app no sabría cuál mostrar.
    por_clave: dict[str, list[tuple[Path, dict]]] = {}
    for ruta, ficha in validas.items():
        por_clave.setdefault(clave(ficha["marca"], ficha["modelo"], ficha["cilindraje_cc"]), []).append((ruta, ficha))
    for grupo in por_clave.values():
        grupo.sort(key=lambda par: par[1]["anio_desde"])
        for (ruta_a, a), (ruta_b, b) in zip(grupo, grupo[1:]):
            if b["anio_desde"] <= a["anio_hasta"]:
                errores.setdefault(ruta_b, []).append(
                    f"los años se cruzan con {ruta_a.name} ({a['anio_desde']}-{a['anio_hasta']})"
                )
    return errores


def main(argv: list[str]) -> int:
    carpeta = Path(argv[1]) if len(argv) > 1 else Path(__file__).resolve().parents[2] / "fichas"
    if not carpeta.is_dir():
        print(f"No existe la carpeta {carpeta}")
        return 1
    fichas, _ = cargar_fichas(carpeta)
    errores = validar_carpeta(carpeta)
    total = len(set(fichas) | set(errores))
    for ruta, lista in sorted(errores.items()):
        print(f"\n✗ {ruta.name}")
        for e in lista:
            print(f"    - {e}")
    print(f"\n{total - len(errores)} de {total} fichas válidas.")
    return 1 if errores else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
