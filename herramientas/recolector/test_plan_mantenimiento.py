"""Pruebas unitarias de extracción de planes de mantenimiento y datos de servicio desde PDFs oficiales."""
import datetime
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock

CARPETA_RECOLECTOR = Path(__file__).resolve().parent
CARPETA_FICHAS_TOOLS = CARPETA_RECOLECTOR.parent / "fichas"
FIXTURES_PDF = CARPETA_RECOLECTOR / "fixtures" / "pdfs"

for p in (str(CARPETA_RECOLECTOR), str(CARPETA_FICHAS_TOOLS)):
    if p not in sys.path:
        sys.path.insert(0, p)

import adaptadores  # noqa: E402
import ficha_tecnica  # noqa: E402
import plan_mantenimiento  # noqa: E402
import recolectar  # noqa: E402
import robots_guard  # noqa: E402
import validar  # noqa: E402


def _cargar_fixture_pdf(nombre: str) -> dict:
    return json.loads((FIXTURES_PDF / nombre).read_text(encoding="utf-8"))


def _doc_ficha_con_plan(fixture: dict, pagina: int, tareas: list, datos_servicio: dict | None) -> dict:
    doc = {
        "marca": fixture["marca"],
        "modelo": fixture["modelo"],
        "cilindraje_cc": 155 if "aerox" in fixture["modelo"].lower() else 200,
        "anio_desde": 2026,
        "anio_hasta": 2026,
        "anio_origen": "deteccion",
        "ficha_tecnica": {
            "cilindraje": {
                "texto_original": "155 cc",
                "fuente": "https://www.incolmotos-yamaha.com.co/vehiculo/test/",
                "capturado": "2026-11-18",
            }
        },
        "url_manual": fixture["url"],
        "verificacion": {"metodo": ficha_tecnica.METODO_VERIFICACION, "fecha": "2026-11-18"},
        "plan_mantenimiento": {
            "fuente": {
                "url": fixture["url"],
                "sha256": fixture["sha256"],
                "pagina": pagina,
                "titulo": f"Manual oficial {fixture['marca']} {fixture['modelo']}",
            },
            "tareas": tareas,
        },
    }
    if datos_servicio:
        doc["datos_servicio"] = datos_servicio
    return doc


class PlantillasRealesPdfTest(unittest.TestCase):
    def _verificar_yamaha(self, archivo: str, min_tareas: int, campos_servicio_esperados: tuple[str, ...]):
        fx = _cargar_fixture_pdf(archivo)
        paginas_mant = [(p[0], p[1], p[2]) for p in fx["paginas_mant"]]
        paginas_espec = [(p[0], p[1]) for p in fx["paginas_espec"]]
        pag, tareas = plan_mantenimiento.extraer_plan_yamaha_desde_paginas(paginas_mant)
        self.assertIsNotNone(pag)
        self.assertGreaterEqual(len(tareas), min_tareas)
        datos_serv = plan_mantenimiento.extraer_datos_servicio_pdf(paginas_espec)
        for campo in campos_servicio_esperados:
            self.assertIn(campo, datos_serv, f"{archivo}: falta {campo}")
        doc = _doc_ficha_con_plan(fx, pag, tareas, datos_serv)
        self.assertEqual([], validar.validar_ficha(doc, datetime.date(2026, 11, 18)))
        return tareas, datos_serv

    def _verificar_bajaj(self, archivo: str, min_tareas: int, campos_servicio_esperados: tuple[str, ...]):
        fx = _cargar_fixture_pdf(archivo)
        paginas_mant = [(p[0], p[1], p[2]) for p in fx["paginas_mant"]]
        paginas_espec = [(p[0], p[1]) for p in fx["paginas_espec"]]
        pag, tareas = plan_mantenimiento.extraer_plan_bajaj_desde_paginas(paginas_mant)
        self.assertIsNotNone(pag)
        self.assertGreaterEqual(len(tareas), min_tareas)
        datos_serv = plan_mantenimiento.extraer_datos_servicio_pdf(paginas_espec)
        for campo in campos_servicio_esperados:
            self.assertIn(campo, datos_serv, f"{archivo}: falta {campo}")
        doc = _doc_ficha_con_plan(fx, pag, tareas, datos_serv)
        self.assertEqual([], validar.validar_ficha(doc, datetime.date(2026, 11, 18)))
        return tareas, datos_serv

    def test_fixtures_yamaha(self):
        self._verificar_yamaha(
            "yamaha_aerox_155.json",
            12,
            ("viscosidad_aceite", "capacidad_aceite", "liquido_frenos", "presion_llanta_delantera", "presion_llanta_trasera"),
        )
        self._verificar_yamaha(
            "yamaha_fz3.json",
            14,
            ("viscosidad_aceite", "capacidad_aceite", "liquido_frenos"),
        )
        self._verificar_yamaha(
            "yamaha_fz25.json",
            14,
            ("viscosidad_aceite", "capacidad_aceite", "liquido_frenos", "presion_llanta_delantera", "presion_llanta_trasera"),
        )
        tareas_mt03, _ = self._verificar_yamaha(
            "yamaha_mt03.json",
            12,
            ("viscosidad_aceite", "capacidad_aceite", "liquido_frenos"),
        )
        embrague = next((t for t in tareas_mt03 if "embrague" in t["componente"].lower()), None)
        self.assertIsNotNone(embrague)
        self.assertEqual("Revisar y ajustar", embrague["accion"])
        self.assertEqual(6, embrague["intervalo_meses"])

    def test_fixtures_bajaj(self):
        self._verificar_bajaj(
            "bajaj_ns200.json",
            35,
            ("viscosidad_aceite", "capacidad_aceite", "presion_llanta_delantera", "presion_llanta_trasera", "holgura_cadena", "liquido_frenos"),
        )
        self._verificar_bajaj(
            "bajaj_boxer_150x.json",
            30,
            ("viscosidad_aceite", "capacidad_aceite", "presion_llanta_delantera", "presion_llanta_trasera", "holgura_cadena"),
        )
        self._verificar_bajaj(
            "bajaj_boxer_ct100.json",
            24,
            ("viscosidad_aceite", "capacidad_aceite", "presion_llanta_delantera", "presion_llanta_trasera", "holgura_cadena"),
        )
        # Dominar 400 valida también la reconstrucción sin bordes de la página 52
        self._verificar_bajaj(
            "bajaj_dominar_400.json",
            25,
            ("viscosidad_aceite", "capacidad_aceite", "presion_llanta_delantera", "presion_llanta_trasera", "holgura_cadena"),
        )


class CotejoYSeguridadPdfTest(unittest.TestCase):
    def test_cotejo_literal_rechaza_componente_o_intervalo_inexistente(self):
        tabla = [
            ["Elemento", "Comprobación u operación", "1000 km", "6000 km", "12000 km", "Anual"],
            ["Aceite de motor", "Cambiar", "√", "√", "√", "√"],
            ["Bujía", "Cambiar", "", "√", "√", ""],
        ]
        # El texto real de la página NO menciona "Bujía" (simulando tabla corrupta/inventada)
        texto_incompleto = (
            "Elemento Comprobación u operación Lectura del cuentakilómetros "
            "1000 km 6000 km 12000 km Anual Aceite de motor Cambiar"
        )
        _, tareas = plan_mantenimiento.extraer_plan_yamaha_desde_paginas([(40, texto_incompleto, [tabla])])
        nombres = [t["componente"] for t in tareas]
        self.assertIn("Aceite de motor", nombres)
        self.assertNotIn("Bujía", nombres)

        # Intervalo inventado (9000 km no está en la página)
        self.assertFalse(plan_mantenimiento._cotejar_intervalo_en_texto(9000, None, texto_incompleto.lower()))
        self.assertTrue(plan_mantenimiento._cotejar_intervalo_en_texto(6000, 12, texto_incompleto.lower()))

    def test_detecta_pdf_escaneado_o_en_curvas(self):
        pdf_escaneado = MagicMock()
        paginas_vacias = []
        for _ in range(10):
            p = MagicMock()
            p.extract_text.return_value = "   "
            paginas_vacias.append(p)
        pdf_escaneado.pages = paginas_vacias
        self.assertFalse(plan_mantenimiento.pdf_tiene_texto_extraible(pdf_escaneado))

        pdf_texto = MagicMock()
        p_texto = MagicMock()
        p_texto.extract_text.return_value = "Tabla de mantenimiento periódico " * 12
        pdf_texto.pages = [p_texto]
        self.assertTrue(plan_mantenimiento.pdf_tiene_texto_extraible(pdf_texto))

    def test_rechaza_url_fuera_de_lista_blanca(self):
        plan, serv, err = plan_mantenimiento.extraer_desde_pdf(
            "Yamaha",
            "FZ25",
            "https://sitio-no-oficial.com/manual.pdf",
            ["incolmotos-yamaha.com.co"],
        )
        self.assertIsNone(plan)
        self.assertIsNone(serv)
        self.assertIn("lista blanca", err.lower())


class IntegracionRecolectorPlanesTest(unittest.TestCase):
    def test_recolector_integra_plan_cachea_y_respeta_sin_planes(self):
        tmp = Path(tempfile.mkdtemp())
        ruta_fuentes = tmp / "fuentes.json"
        ruta_snapshot = tmp / "catalog.json"
        ruta_fichas = tmp / "fichas"
        ruta_fichas.mkdir()

        url_manual = "https://www.incolmotos-yamaha.com.co/wp-content/uploads/2019/08/B8V2-FZ-250A.pdf"
        ruta_fuentes.write_text(
            json.dumps(
                {
                    "Yamaha": {
                        "dominios": ["incolmotos-yamaha.com.co"],
                        "catalogo": ["https://www.incolmotos-yamaha.com.co/vehiculos/"],
                        "adaptador": "yamaha",
                    }
                }
            ),
            encoding="utf-8",
        )
        ruta_snapshot.write_text("{}", encoding="utf-8")

        modelo = adaptadores.ModeloOficial(
            modelo="FZ25 ABS",
            cilindraje_cc=249,
            categoria="Street",
            url_fuente="https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/",
            url_manual=url_manual,
            anio_fuente=2026,
            ficha_tecnica={
                "cilindraje": {
                    "texto_original": "249 cc",
                    "fuente": "https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/",
                    "capturado": "2026-11-18",
                }
            },
        )

        class FakeYamaha:
            def __init__(self, marca, cfg, client, hoy=None, incluir_fichas=True):
                pass

            def recolectar(self):
                return [modelo]

        plan_falso = {
            "fuente": {
                "url": url_manual,
                "sha256": "a" * 64,
                "pagina": 44,
                "titulo": "Manual oficial Yamaha FZ25 ABS",
            },
            "tareas": [
                {
                    "componente": "Aceite de motor",
                    "accion": "Cambiar",
                    "intervalo_km": 5000,
                    "intervalo_meses": 6,
                }
            ],
        }
        servicio_falso = {"capacidad_aceite": {"valor": "1.35 L", "pagina": 66}}

        llamadas = []

        def fake_extractor(*args, **kwargs):
            llamadas.append(args)
            return plan_falso, servicio_falso, None

        guard = robots_guard.RobotsGuard(fetcher=lambda _: (200, "User-agent: *\nAllow: /\n"))

        # 1. Primera ejecución: llama extractor y guarda plan + servicio
        _, alertas1 = recolectar.ejecutar_recoleccion(
            ruta_fuentes=ruta_fuentes,
            ruta_snapshot=ruta_snapshot,
            ruta_fichas=ruta_fichas,
            simular=True,
            incluir_fichas=True,
            incluir_planes=True,
            hoy="2026-11-18",
            adaptadores_registry={"yamaha": FakeYamaha},
            robots_guard=guard,
            plan_extractor=fake_extractor,
        )
        self.assertEqual([], alertas1)
        self.assertEqual(1, len(llamadas))
        ficha_file = ruta_fichas / "yamaha_fz25-abs_249_2026-2026.json"
        self.assertTrue(ficha_file.is_file())
        data_ficha = json.loads(ficha_file.read_text(encoding="utf-8"))
        self.assertEqual(plan_falso, data_ficha["plan_mantenimiento"])
        self.assertEqual(servicio_falso, data_ficha["datos_servicio"])
        self.assertEqual({}, validar.validar_carpeta(ruta_fichas, datetime.date(2026, 11, 18)))

        # 2. Segunda ejecución sin cambios y sin refrescar_pdfs: reutiliza plan sin invocar extractor
        _, alertas2 = recolectar.ejecutar_recoleccion(
            ruta_fuentes=ruta_fuentes,
            ruta_snapshot=ruta_snapshot,
            ruta_fichas=ruta_fichas,
            simular=True,
            incluir_fichas=True,
            incluir_planes=True,
            refrescar_pdfs=False,
            hoy="2026-11-19",
            adaptadores_registry={"yamaha": FakeYamaha},
            robots_guard=guard,
            plan_extractor=fake_extractor,
        )
        self.assertEqual([], alertas2)
        self.assertEqual(1, len(llamadas))

        # 3. Ejecución con refrescar_pdfs=True: vuelve a invocar extractor
        _, alertas3 = recolectar.ejecutar_recoleccion(
            ruta_fuentes=ruta_fuentes,
            ruta_snapshot=ruta_snapshot,
            ruta_fichas=ruta_fichas,
            simular=True,
            incluir_fichas=True,
            incluir_planes=True,
            refrescar_pdfs=True,
            hoy="2026-11-20",
            adaptadores_registry={"yamaha": FakeYamaha},
            robots_guard=guard,
            plan_extractor=fake_extractor,
        )
        self.assertEqual([], alertas3)
        self.assertEqual(2, len(llamadas))

        # 4. Ejecución con incluir_planes=False (--sin-planes) + cambio de specs en 2027: conserva plan previo
        modelo_2027 = adaptadores.ModeloOficial(
            modelo="FZ25 ABS",
            cilindraje_cc=249,
            categoria="Street",
            url_fuente="https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/",
            url_manual=url_manual,
            anio_fuente=2027,
            ficha_tecnica={
                "cilindraje": {
                    "texto_original": "249 cc",
                    "fuente": "https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/",
                    "capturado": "2027-01-10",
                },
                "peso": {
                    "texto_original": "153 kg",
                    "fuente": "https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/",
                    "capturado": "2027-01-10",
                },
            },
        )
        FakeYamaha.recolectar = lambda self: [modelo_2027]
        _, alertas4 = recolectar.ejecutar_recoleccion(
            ruta_fuentes=ruta_fuentes,
            ruta_snapshot=ruta_snapshot,
            ruta_fichas=ruta_fichas,
            simular=True,
            incluir_fichas=True,
            incluir_planes=False,
            hoy="2027-01-10",
            adaptadores_registry={"yamaha": FakeYamaha},
            robots_guard=guard,
            plan_extractor=fake_extractor,
        )
        self.assertEqual([], alertas4)
        self.assertEqual(2, len(llamadas))
        ficha_2027 = json.loads((ruta_fichas / "yamaha_fz25-abs_249_2027-2027.json").read_text(encoding="utf-8"))
        self.assertEqual(plan_falso, ficha_2027["plan_mantenimiento"])


if __name__ == "__main__":
    unittest.main()
