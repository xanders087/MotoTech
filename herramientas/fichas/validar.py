#!/usr/bin/env python3
"""Valida las fichas técnicas de la carpeta fichas/.

Soporta:
- Fichas v3 generadas por el recolector oficial (`verificacion`, `ficha_tecnica` con fuente HTTPS,
  opcionalmente `datos_servicio` y `plan_mantenimiento` con PDF + sha256 + página).
- Fichas manuales clásicas (`manual`, `revision`, `ficha_tecnica` con `valor` y `pagina`).

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

CAMPOS_SERVICIO = (
    "capacidad_aceite",
    "viscosidad_aceite",
    "presion_llanta_delantera",
    "presion_llanta_trasera",
    "holgura_cadena",
    "liquido_frenos",
)

CAMPOS_ESPECIFICACION = (
    "cilindraje",
    "tipo_motor",
    "potencia_maxima",
    "torque_maximo",
    "alimentacion",
    "refrigeracion",
    "arranque",
    "relacion_compresion",
    "tipo_transmision",
    "numero_velocidades",
    "transmision_final",
    "freno_delantero",
    "freno_trasero",
    "abs",
    "suspension_delantera",
    "suspension_trasera",
    "llanta_delantera",
    "llanta_trasera",
    "capacidad_tanque",
    "peso",
    "altura_asiento",
    "distancia_ejes",
)

CAMPOS_FICHA = CAMPOS_SERVICIO + CAMPOS_ESPECIFICACION

CAMPOS_RAIZ = {
    "marca", "modelo", "cilindraje_cc", "anio_desde", "anio_hasta",
    "anio_origen", "url_manual", "manual", "revision", "verificacion",
    "ficha_tecnica", "datos_servicio", "plan_mantenimiento",
}
CAMPOS_MANUAL = {"titulo", "edicion", "url"}
CAMPOS_REVISION = {"revisado_por", "fecha"}
CAMPOS_VERIFICACION = {"metodo", "fecha"}
CAMPOS_DATO_MANUAL = {"valor", "pagina"}
CAMPOS_DATO_WEB = {"texto_original", "valor_normalizado", "fuente", "capturado"}
CAMPOS_PLAN_TAREA = {"componente", "accion", "intervalo_km", "intervalo_meses", "pagina"}
CAMPOS_PLAN_TAREA_V3 = {"componente", "accion", "intervalo_km", "intervalo_meses"}
CAMPOS_FUENTE_PDF = {"url", "sha256", "pagina", "titulo"}
ORIGENES_ANIO = {"deteccion", "fuente"}

# Textos de relleno que no son un dato real.
RELLENO = re.compile(r"^(todo|tbd|pendiente|por definir|n/?a|\?+|x+|-+|—+|\.+)$", re.IGNORECASE)
_RE_SHA256 = re.compile(r"^[0-9a-f]{64}$")


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


def _fecha_iso_valida(valor, hoy: datetime.date) -> tuple[bool, str | None]:
    if not isinstance(valor, str):
        return False, "debe tener el formato AAAA-MM-DD"
    try:
        fecha = datetime.date.fromisoformat(valor)
        if fecha > hoy:
            return False, "no puede ser una fecha futura"
        return True, None
    except ValueError:
        return False, "debe tener el formato AAAA-MM-DD"


def _sobrantes(objeto: dict, permitidos: set, donde: str, errores: list) -> None:
    for campo in sorted(set(objeto) - permitidos):
        errores.append(f"{donde}: campo desconocido \"{campo}\" (¿error de escritura?)")


def _validar_dato_manual(dato: dict, donde: str, errores: list) -> int:
    _sobrantes(dato, CAMPOS_DATO_MANUAL, donde, errores)
    if not _texto_real(dato.get("valor")):
        errores.append(f"{donde}.valor: falta el valor (si el manual no lo trae, usa null en todo el dato)")
    if not _es_entero(dato.get("pagina")) or dato["pagina"] < 1:
        errores.append(f"{donde}.pagina: falta la página del manual")
    return 1


def _validar_dato_web(dato: dict, donde: str, hoy: datetime.date, errores: list) -> int:
    _sobrantes(dato, CAMPOS_DATO_WEB, donde, errores)
    if not _texto_real(dato.get("texto_original")):
        errores.append(f"{donde}.texto_original: falta el texto literal de la fuente oficial")
    norm = dato.get("valor_normalizado")
    if norm is not None and not _texto_real(norm):
        errores.append(f"{donde}.valor_normalizado: debe ser texto real o null")
    fuente = dato.get("fuente")
    if not isinstance(fuente, str) or not fuente.startswith("https://"):
        errores.append(f"{donde}.fuente: debe empezar por https://")
    ok_fecha, msg_fecha = _fecha_iso_valida(dato.get("capturado"), hoy)
    if not ok_fecha:
        errores.append(f"{donde}.capturado: {msg_fecha}")
    return 1


def _validar_tarea_plan(
    tarea: dict,
    donde: str,
    vistos: set[str],
    requiere_pagina_propia: bool,
    errores: list,
) -> int:
    permitidos = CAMPOS_PLAN_TAREA if requiere_pagina_propia else CAMPOS_PLAN_TAREA_V3
    _sobrantes(tarea, permitidos, donde, errores)
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
    if requiere_pagina_propia:
        if not _es_entero(tarea.get("pagina")) or tarea["pagina"] < 1:
            errores.append(f"{donde}.pagina: falta la página del manual")
    return 1


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

    if "anio_origen" in ficha and ficha["anio_origen"] is not None:
        if ficha["anio_origen"] not in ORIGENES_ANIO:
            errores.append("anio_origen: debe ser 'deteccion' o 'fuente'")

    if "url_manual" in ficha and ficha["url_manual"] is not None:
        if not (isinstance(ficha["url_manual"], str) and ficha["url_manual"].startswith("https://")):
            errores.append("url_manual: debe empezar por https:// o ser null")

    es_v3 = "verificacion" in ficha

    if es_v3:
        verif = ficha.get("verificacion")
        if not isinstance(verif, dict):
            errores.append("verificacion: debe ser un objeto con metodo y fecha")
        else:
            _sobrantes(verif, CAMPOS_VERIFICACION, "verificacion", errores)
            if not _texto_real(verif.get("metodo")):
                errores.append("verificacion.metodo: falta el método de verificación")
            ok_f, msg_f = _fecha_iso_valida(verif.get("fecha"), hoy)
            if not ok_f:
                errores.append(f"verificacion.fecha: {msg_f}")
    else:
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
            ok_f, msg_f = _fecha_iso_valida(revision.get("fecha"), hoy)
            if not ok_f:
                errores.append(f"revision.fecha: {msg_f}")

    datos = 0
    tecnica = ficha.get("ficha_tecnica", {})
    if not isinstance(tecnica, dict):
        errores.append("ficha_tecnica: debe ser un objeto")
    else:
        permitidos_tecnica = set(CAMPOS_ESPECIFICACION) if es_v3 else set(CAMPOS_FICHA)
        _sobrantes(tecnica, permitidos_tecnica, "ficha_tecnica", errores)
        for campo in sorted(permitidos_tecnica):
            dato = tecnica.get(campo)
            if dato is None:
                continue
            donde = f"ficha_tecnica.{campo}"
            if not isinstance(dato, dict):
                errores.append(f"{donde}: debe ser null o un objeto válido")
                continue
            if "texto_original" in dato or es_v3:
                datos += _validar_dato_web(dato, donde, hoy, errores)
            else:
                datos += _validar_dato_manual(dato, donde, errores)

    datos_servicio = ficha.get("datos_servicio")
    if datos_servicio is not None:
        if not isinstance(datos_servicio, dict):
            errores.append("datos_servicio: debe ser un objeto")
        else:
            _sobrantes(datos_servicio, set(CAMPOS_SERVICIO), "datos_servicio", errores)
            for campo in CAMPOS_SERVICIO:
                dato = datos_servicio.get(campo)
                if dato is None:
                    continue
                donde = f"datos_servicio.{campo}"
                if not isinstance(dato, dict):
                    errores.append(f"{donde}: debe ser null o un objeto con valor y pagina")
                    continue
                datos += _validar_dato_manual(dato, donde, errores)

    plan = ficha.get("plan_mantenimiento")
    if plan is None:
        pass
    elif isinstance(plan, list):
        vistos: set[str] = set()
        for i, tarea in enumerate(plan, start=1):
            donde = f"plan_mantenimiento[{i}]"
            if not isinstance(tarea, dict):
                errores.append(f"{donde}: debe ser un objeto")
                continue
            datos += _validar_tarea_plan(tarea, donde, vistos, requiere_pagina_propia=True, errores=errores)
    elif isinstance(plan, dict):
        _sobrantes(plan, {"fuente", "tareas"}, "plan_mantenimiento", errores)
        fuente_pdf = plan.get("fuente")
        if not isinstance(fuente_pdf, dict):
            errores.append("plan_mantenimiento.fuente: falta el origen PDF del plan")
        else:
            _sobrantes(fuente_pdf, CAMPOS_FUENTE_PDF, "plan_mantenimiento.fuente", errores)
            url_pdf = fuente_pdf.get("url")
            if not isinstance(url_pdf, str) or not url_pdf.startswith("https://"):
                errores.append("plan_mantenimiento.fuente.url: debe empezar por https://")
            sha = fuente_pdf.get("sha256")
            if not isinstance(sha, str) or not _RE_SHA256.match(sha.lower()):
                errores.append("plan_mantenimiento.fuente.sha256: debe ser un hash SHA-256 hexadecimal de 64 caracteres")
            if not _es_entero(fuente_pdf.get("pagina")) or fuente_pdf["pagina"] < 1:
                errores.append("plan_mantenimiento.fuente.pagina: falta la página del PDF")
            if not _texto_real(fuente_pdf.get("titulo")):
                errores.append("plan_mantenimiento.fuente.titulo: falta el título del documento")

        tareas = plan.get("tareas")
        if not isinstance(tareas, list) or not tareas:
            errores.append("plan_mantenimiento.tareas: debe ser una lista no vacía")
        else:
            vistos_v3: set[str] = set()
            for i, tarea in enumerate(tareas, start=1):
                donde = f"plan_mantenimiento.tareas[{i}]"
                if not isinstance(tarea, dict):
                    errores.append(f"{donde}: debe ser un objeto")
                    continue
                datos += _validar_tarea_plan(tarea, donde, vistos_v3, requiere_pagina_propia=False, errores=errores)
    else:
        errores.append("plan_mantenimiento: debe ser una lista, un objeto o null")

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
