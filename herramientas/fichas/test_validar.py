"""Pruebas del validador. Los valores son ficticios a propósito: no describen ninguna moto real."""
import copy
import datetime
import json
import tempfile
import unittest
from pathlib import Path

import subir
import validar

HOY = datetime.date(2026, 11, 16)


def ficha_valida() -> dict:
    return {
        "marca": "Marca Prueba",
        "modelo": "Modelo X 1",
        "cilindraje_cc": 123,
        "anio_desde": 2020,
        "anio_hasta": 2022,
        "manual": {"titulo": "Manual de prueba", "edicion": "1a", "url": None},
        "revision": {"revisado_por": "Persona de prueba", "fecha": "2026-11-01"},
        "ficha_tecnica": {
            "capacidad_aceite": {"valor": "valor de prueba", "pagina": 10},
            "holgura_cadena": None,
        },
        "plan_mantenimiento": [
            {"componente": "Tarea A", "accion": "Revisar", "intervalo_km": 1000, "intervalo_meses": None, "pagina": 20}
        ],
    }


class ValidarFichaTest(unittest.TestCase):
    def errores(self, cambio):
        ficha = ficha_valida()
        cambio(ficha)
        return validar.validar_ficha(ficha, HOY)

    def test_ficha_completa_es_valida(self):
        self.assertEqual([], validar.validar_ficha(ficha_valida(), HOY))

    def test_la_plantilla_sin_llenar_no_es_valida(self):
        plantilla = json.loads((Path(__file__).resolve().parents[2] / "fichas" / "_plantilla.json").read_text("utf-8"))
        self.assertTrue(validar.validar_ficha(plantilla, HOY))

    def test_dato_sin_pagina_se_rechaza(self):
        e = self.errores(lambda f: f["ficha_tecnica"]["capacidad_aceite"].pop("pagina"))
        self.assertIn("ficha_tecnica.capacidad_aceite.pagina: falta la página del manual", e)

    def test_dato_con_valor_de_relleno_se_rechaza(self):
        for relleno in ("", "  ", "TODO", "??", "N/A", "pendiente"):
            e = self.errores(lambda f: f["ficha_tecnica"]["capacidad_aceite"].update(valor=relleno))
            self.assertTrue(any("capacidad_aceite.valor" in x for x in e), relleno)

    def test_dato_ausente_en_el_manual_se_deja_en_null(self):
        self.assertEqual([], self.errores(lambda f: f["ficha_tecnica"].update(liquido_frenos=None)))

    def test_falta_el_manual(self):
        self.assertTrue(any(x.startswith("manual") for x in self.errores(lambda f: f.pop("manual"))))
        self.assertIn("manual.titulo: falta el título del manual", self.errores(lambda f: f["manual"].update(titulo="")))

    def test_url_del_manual_debe_ser_https(self):
        self.assertTrue(self.errores(lambda f: f["manual"].update(url="http://ejemplo.com/m.pdf")))
        self.assertEqual([], self.errores(lambda f: f["manual"].update(url="https://ejemplo.com/m.pdf")))

    def test_revision_obligatoria_y_no_futura(self):
        self.assertTrue(self.errores(lambda f: f["revision"].update(revisado_por="")))
        self.assertTrue(self.errores(lambda f: f["revision"].update(fecha="01/11/2026")))
        self.assertTrue(self.errores(lambda f: f["revision"].update(fecha="2027-01-01")))

    def test_tarea_necesita_intervalo_y_pagina(self):
        self.assertTrue(self.errores(lambda f: f["plan_mantenimiento"][0].update(intervalo_km=None)))
        self.assertTrue(self.errores(lambda f: f["plan_mantenimiento"][0].update(intervalo_km=0)))
        self.assertTrue(self.errores(lambda f: f["plan_mantenimiento"][0].update(pagina=0)))
        self.assertEqual([], self.errores(
            lambda f: f["plan_mantenimiento"][0].update(intervalo_km=None, intervalo_meses=12)))

    def test_tarea_repetida(self):
        e = self.errores(lambda f: f["plan_mantenimiento"].append(
            {"componente": " tarea  a ", "accion": None, "intervalo_km": 5, "intervalo_meses": None, "pagina": 3}))
        self.assertTrue(any("repetido" in x for x in e))

    def test_anios_y_cilindraje(self):
        self.assertTrue(self.errores(lambda f: f.update(anio_desde=2023)))
        self.assertTrue(self.errores(lambda f: f.update(anio_hasta=2040)))
        self.assertTrue(self.errores(lambda f: f.update(cilindraje_cc="123cc")))
        self.assertTrue(self.errores(lambda f: f.update(cilindraje_cc=True)))

    def test_campo_mal_escrito(self):
        e = self.errores(lambda f: f["ficha_tecnica"].update(capasidad_aceite={"valor": "x1", "pagina": 1}))
        self.assertTrue(any("campo desconocido" in x for x in e))

    def test_ficha_sin_ningun_dato(self):
        e = self.errores(lambda f: f.update(ficha_tecnica={}, plan_mantenimiento=[]))
        self.assertTrue(any("ningún dato" in x for x in e))


class ClaveTest(unittest.TestCase):
    def test_clave_coincide_con_la_de_la_app(self):
        # VehicleKey.of en la app: minúsculas, espacios simples, cilindraje solo con dígitos.
        self.assertEqual("marca prueba|modelo x 1|123", validar.clave("  Marca   Prueba ", "MODELO X 1", 123))

    def test_id_de_ficha(self):
        self.assertEqual("marca-prueba_modelo-x-1_123_2020-2022", validar.id_ficha(ficha_valida()))

    def test_documento_para_firestore(self):
        doc = subir.documento(ficha_valida())
        self.assertEqual("marca prueba|modelo x 1|123", doc["clave"])
        self.assertEqual("publicada", doc["estado"])
        self.assertEqual(2020, doc["anio_desde"])


class ValidarCarpetaTest(unittest.TestCase):
    def carpeta(self, fichas: dict) -> Path:
        tmp = Path(tempfile.mkdtemp())
        for nombre, contenido in fichas.items():
            texto = contenido if isinstance(contenido, str) else json.dumps(contenido)
            (tmp / nombre).write_text(texto, encoding="utf-8")
        return tmp

    def test_carpeta_valida_e_ignora_plantillas(self):
        tmp = self.carpeta({"marca-prueba_modelo-x-1_123_2020-2022.json": ficha_valida(), "_plantilla.json": {}})
        self.assertEqual({}, validar.validar_carpeta(tmp, HOY))

    def test_nombre_de_archivo_incorrecto(self):
        tmp = self.carpeta({"mi-moto.json": ficha_valida()})
        e = validar.validar_carpeta(tmp, HOY)
        self.assertIn("marca-prueba_modelo-x-1_123_2020-2022.json", list(e.values())[0][0])

    def test_json_roto(self):
        tmp = self.carpeta({"rota.json": "{ no es json"})
        self.assertTrue(any("JSON válido" in x for x in list(validar.validar_carpeta(tmp, HOY).values())[0]))

    def test_anios_cruzados_entre_fichas(self):
        otra = copy.deepcopy(ficha_valida())
        otra.update(anio_desde=2022, anio_hasta=2024)
        tmp = self.carpeta({
            "marca-prueba_modelo-x-1_123_2020-2022.json": ficha_valida(),
            "marca-prueba_modelo-x-1_123_2022-2024.json": otra,
        })
        e = validar.validar_carpeta(tmp, HOY)
        self.assertEqual(1, len(e))
        self.assertTrue(any("se cruzan" in x for x in list(e.values())[0]))

    def test_anios_consecutivos_no_se_cruzan(self):
        otra = copy.deepcopy(ficha_valida())
        otra.update(anio_desde=2023, anio_hasta=2024)
        tmp = self.carpeta({
            "marca-prueba_modelo-x-1_123_2020-2022.json": ficha_valida(),
            "marca-prueba_modelo-x-1_123_2023-2024.json": otra,
        })
        self.assertEqual({}, validar.validar_carpeta(tmp, HOY))


if __name__ == "__main__":
    unittest.main()
