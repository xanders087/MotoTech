"""Pruebas unitarias de extracción determinista, fixtures reales y versionado de fichas técnicas."""
import datetime
import json
import sys
import tempfile
import unittest
from pathlib import Path

CARPETA_RECOLECTOR = Path(__file__).resolve().parent
CARPETA_FICHAS_TOOLS = CARPETA_RECOLECTOR.parent / "fichas"
FIXTURES = CARPETA_RECOLECTOR / "fixtures"

for p in (str(CARPETA_RECOLECTOR), str(CARPETA_FICHAS_TOOLS)):
    if p not in sys.path:
        sys.path.insert(0, p)

import adaptadores  # noqa: E402
import ficha_tecnica  # noqa: E402
import recolectar  # noqa: E402
import robots_guard  # noqa: E402
import validar  # noqa: E402


class NormalizacionYValidacionTest(unittest.TestCase):
    def test_sinonimos_y_etiquetas_desconocidas(self):
        self.assertEqual(("cilindraje", None), ficha_tecnica.mapear_etiqueta("Cilindrada:"))
        self.assertEqual(("capacidad_tanque", "gal"), ficha_tecnica.mapear_etiqueta("Capacidad Tanque Combustible (Gal)"))
        self.assertEqual(("peso", None), ficha_tecnica.mapear_etiqueta("PESO**"))
        self.assertEqual(("torque_maximo", None), ficha_tecnica.mapear_etiqueta("Torque máxima"))
        self.assertEqual(("potencia_maxima", "hp"), ficha_tecnica.mapear_etiqueta("Potencia máxima (Hp/rpm)"))
        self.assertEqual((None, None), ficha_tecnica.mapear_etiqueta("Precio de lista"))
        self.assertEqual((None, None), ficha_tecnica.mapear_etiqueta("Relación de transmisión en 1ra"))

    def test_rechaza_dato_que_no_aparece_literalmente_en_fuente(self):
        ficha = ficha_tecnica.construir_ficha_tecnica(
            pares=[("Cilindraje", "199 cc"), ("Capacidad del tanque", "12 L")],
            url_fuente="https://grupouma.com/colombia/motos/pulsar/ns200/",
            texto_fuente="Pulsar NS200 Cilindraje 199 cc Potencia 24 Hp",
            hoy="2026-11-18",
        )
        self.assertIn("cilindraje", ficha)
        self.assertNotIn("capacidad_tanque", ficha)

    def test_conversion_unidades_y_rechazo_fuera_de_rango(self):
        # Galones a litros
        self.assertEqual(("2.6", "9.8 L"), ficha_tecnica.validar_y_normalizar_dato("capacidad_tanque", "2.6", "gal"))
        # Libras a kg
        self.assertEqual(("220 lb", "99.8 kg"), ficha_tecnica.validar_y_normalizar_dato("peso", "220 lb"))
        # Centímetros a milímetros
        self.assertEqual(("78 cm", "780 mm"), ficha_tecnica.validar_y_normalizar_dato("altura_asiento", "78 cm"))
        # Separador de miles en cilindraje y distancia entre ejes
        self.assertEqual(("1.084 cm³", "1084 cc"), ficha_tecnica.validar_y_normalizar_dato("cilindraje", "1.084 cm³"))
        self.assertEqual(("1,286 mm", "1286 mm"), ficha_tecnica.validar_y_normalizar_dato("distancia_ejes", "1,286 mm"))
        # Unidad proveniente de etiqueta en potencia / torque
        self.assertEqual(("10.34/8.000", "10.34 HP"), ficha_tecnica.validar_y_normalizar_dato("potencia_maxima", "10.34/8.000", "hp"))
        self.assertEqual(("9.3/7.000", "9.3 Nm"), ficha_tecnica.validar_y_normalizar_dato("torque_maximo", "9.3/7.000", "nm"))

        # Fuera de rango o sin unidad requerida
        self.assertIsNone(ficha_tecnica.validar_y_normalizar_dato("cilindraje", "15 cc"))
        self.assertIsNone(ficha_tecnica.validar_y_normalizar_dato("capacidad_tanque", "80 L"))
        self.assertIsNone(ficha_tecnica.validar_y_normalizar_dato("peso", "12 kg"))
        self.assertIsNone(ficha_tecnica.validar_y_normalizar_dato("potencia_maxima", "Alta"))
        self.assertIsNone(ficha_tecnica.validar_y_normalizar_dato("capacidad_tanque", "N/A"))


class FixturesRealesTest(unittest.TestCase):
    def _validar_como_ficha(self, modelo: adaptadores.ModeloOficial, marca: str):
        self.assertIsNotNone(modelo.ficha_tecnica)
        doc = {
            "marca": marca,
            "modelo": modelo.modelo,
            "cilindraje_cc": modelo.cilindraje_cc,
            "anio_desde": modelo.anio_fuente or 2026,
            "anio_hasta": modelo.anio_fuente or 2026,
            "anio_origen": "fuente" if modelo.anio_fuente else "deteccion",
            "ficha_tecnica": modelo.ficha_tecnica,
            "url_manual": modelo.url_manual,
            "verificacion": {"metodo": ficha_tecnica.METODO_VERIFICACION, "fecha": "2026-11-18"},
        }
        errores = validar.validar_ficha(doc, datetime.date(2026, 11, 18))
        self.assertEqual([], errores)

    def test_fixture_yamaha_fz25_api(self):
        data = json.loads((FIXTURES / "yamaha_fz25_api.json").read_text(encoding="utf-8"))
        url = "https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/"
        m = adaptadores.parsear_yamaha_vehiculo_api(data, url, hoy="2026-11-18")
        self.assertIsNotNone(m)
        self.assertEqual("FZ25 ABS", m.modelo)
        self.assertEqual(249, m.cilindraje_cc)
        self.assertIn("cilindraje", m.ficha_tecnica)
        self.assertIn("potencia_maxima", m.ficha_tecnica)
        self.assertIn("torque_maximo", m.ficha_tecnica)
        self.assertEqual("14 L", m.ficha_tecnica["capacidad_tanque"]["valor_normalizado"])
        self._validar_como_ficha(m, "Yamaha")

    def test_fixture_honda_navi_pdp(self):
        html = (FIXTURES / "honda_navi_pdp.html").read_text(encoding="utf-8")
        url = "https://motos.honda.com.co/motos-honda/NAVI/moto-navi"
        ficha = adaptadores.parsear_honda_modelo_html(html, url, hoy="2026-11-18")
        self.assertGreaterEqual(len(ficha), 12)
        self.assertEqual("109 cc", ficha["cilindraje"]["valor_normalizado"])
        self.assertEqual("1286 mm", ficha["distancia_ejes"]["valor_normalizado"])
        self.assertEqual("90/90-12", ficha["llanta_delantera"]["texto_original"])
        self.assertEqual("90/100-10", ficha["llanta_trasera"]["texto_original"])
        modelo = adaptadores.ModeloOficial("Navi", 109, "Navi", url, ficha_tecnica=ficha)
        self._validar_como_ficha(modelo, "Honda")

    def test_fixture_bajaj_ns200_pdp_y_desambiguacion_llantas(self):
        html = (FIXTURES / "bajaj_ns200_pdp.html").read_text(encoding="utf-8")
        url = "https://grupouma.com/colombia/motos/pulsar/pulsar-ns200-fi-abs/"
        m = adaptadores.parsear_bajaj_modelo_html(html, url, hoy="2026-11-18")
        self.assertIsNotNone(m)
        self.assertEqual(199, m.cilindraje_cc)
        self.assertIn("llanta_delantera", m.ficha_tecnica)
        self.assertIn("llanta_trasera", m.ficha_tecnica)
        self.assertNotEqual(
            m.ficha_tecnica["llanta_delantera"]["texto_original"],
            m.ficha_tecnica["llanta_trasera"]["texto_original"],
        )
        self.assertEqual("155 Kg", m.ficha_tecnica["peso"]["texto_original"])
        self._validar_como_ficha(m, "Bajaj")

    def test_fixture_akt_nkd125_pdp(self):
        html = (FIXTURES / "akt_nkd125_pdp.html").read_text(encoding="utf-8")
        url = "https://aktmotos.com/motos-akt/calle/nueva-nkd-125/"
        m = adaptadores.parsear_akt_modelo_html(html, url, hoy="2026-11-18")
        self.assertIsNotNone(m)
        self.assertEqual(124, m.cilindraje_cc)
        self.assertEqual("9.8 L", m.ficha_tecnica["capacidad_tanque"]["valor_normalizado"])
        self.assertEqual("10.34 HP", m.ficha_tecnica["potencia_maxima"]["valor_normalizado"])
        self.assertEqual("9.3 Nm", m.ficha_tecnica["torque_maximo"]["valor_normalizado"])
        self.assertEqual("1270 mm", m.ficha_tecnica["distancia_ejes"]["valor_normalizado"])
        self._validar_como_ficha(m, "AKT")


class VersionadoAnualTest(unittest.TestCase):
    def _spec(self, potencia: str, capturado: str) -> dict:
        return {
            "cilindraje": {
                "texto_original": "149 cc",
                "fuente": "https://www.incolmotos-yamaha.com.co/vehiculo/fz15/1/",
                "capturado": capturado,
            },
            "potencia_maxima": {
                "texto_original": potencia,
                "fuente": "https://www.incolmotos-yamaha.com.co/vehiculo/fz15/1/",
                "capturado": capturado,
            },
        }

    def test_ciclo_completo_versionado_por_deteccion(self):
        carpeta = Path(tempfile.mkdtemp())

        # 1. Primera detección en 2026
        c1 = ficha_tecnica.sincronizar_ficha_en_carpeta(
            carpeta, "Yamaha", "FZ15", 149,
            self._spec("12.2 HP @ 7500 rpm", "2026-11-18"),
            hoy=datetime.date(2026, 11, 18),
        )
        self.assertEqual(1, len(c1))
        self.assertTrue((carpeta / "yamaha_fz15_149_2026-2026.json").is_file())
        self.assertEqual("deteccion", c1[0]["anio_origen"])

        # 2. Semana siguiente en 2026 con los mismos valores: no-op (evita commits vacíos)
        c2 = ficha_tecnica.sincronizar_ficha_en_carpeta(
            carpeta, "Yamaha", "FZ15", 149,
            self._spec("12.2 HP @ 7500 rpm", "2026-11-25"),
            hoy=datetime.date(2026, 11, 25),
        )
        self.assertEqual([], c2)

        # 3. Mismos valores en 2027 -> extiende anio_hasta y renombra archivo
        c3 = ficha_tecnica.sincronizar_ficha_en_carpeta(
            carpeta, "Yamaha", "FZ15", 149,
            self._spec("12.2 HP @ 7500 rpm", "2027-01-05"),
            hoy=datetime.date(2027, 1, 5),
        )
        self.assertEqual(1, len(c3))
        self.assertFalse((carpeta / "yamaha_fz15_149_2026-2026.json").exists())
        self.assertTrue((carpeta / "yamaha_fz15_149_2026-2027.json").is_file())

        # 4. Cambia la potencia en 2028 -> cierra 2026-2027 (ya estaba en 2027) y crea 2028-2028
        c4 = ficha_tecnica.sincronizar_ficha_en_carpeta(
            carpeta, "Yamaha", "FZ15", 149,
            self._spec("13.0 HP @ 8000 rpm", "2028-02-01"),
            hoy=datetime.date(2028, 2, 1),
        )
        self.assertEqual(1, len(c4))
        self.assertTrue((carpeta / "yamaha_fz15_149_2026-2027.json").is_file())
        self.assertTrue((carpeta / "yamaha_fz15_149_2028-2028.json").is_file())

        # 5. Nuevo cambio en 2029 tras extender a 2029? Simulemos cambio directo en 2029 sobre 2028-2028:
        # primero extendamos 2028-2028 en 2029? No, cambiemos directo en 2029 -> cierra hasta 2028 y abre 2029.
        c5 = ficha_tecnica.sincronizar_ficha_en_carpeta(
            carpeta, "Yamaha", "FZ15", 149,
            self._spec("13.5 HP @ 8200 rpm", "2029-03-01"),
            hoy=datetime.date(2029, 3, 1),
        )
        self.assertEqual(1, len(c5))
        self.assertTrue((carpeta / "yamaha_fz15_149_2029-2029.json").is_file())

        # Toda la carpeta debe validar sin solapamientos
        self.assertEqual({}, validar.validar_carpeta(carpeta, datetime.date(2029, 3, 1)))

    def test_anio_explicito_de_fuente_y_recolector_actualiza_tiene_ficha(self):
        tmp = Path(tempfile.mkdtemp())
        ruta_fuentes = tmp / "fuentes.json"
        ruta_snapshot = tmp / "catalog.json"
        ruta_fichas = tmp / "fichas"
        ruta_fichas.mkdir()

        ruta_fuentes.write_text(json.dumps({
            "Yamaha": {
                "dominios": ["incolmotos-yamaha.com.co"],
                "catalogo": ["https://www.incolmotos-yamaha.com.co/vehiculos/"],
                "adaptador": "yamaha",
            },
        }), encoding="utf-8")
        ruta_snapshot.write_text(json.dumps({
            "Yamaha": [{"modelo": "FZ25 ABS", "cilindraje": "249cc", "tiene_ficha": False}],
        }), encoding="utf-8")

        data = json.loads((FIXTURES / "yamaha_fz25_api.json").read_text(encoding="utf-8"))
        modelo = adaptadores.parsear_yamaha_vehiculo_api(
            data, "https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/", hoy="2026-11-18"
        )

        class FakeYamaha:
            def __init__(self, marca, cfg, client, hoy=None, incluir_fichas=True): pass
            def recolectar(self): return [modelo]

        docs, alertas = recolectar.ejecutar_recoleccion(
            ruta_fuentes=ruta_fuentes,
            ruta_snapshot=ruta_snapshot,
            ruta_fichas=ruta_fichas,
            simular=True,
            escribir_snapshot=True,
            incluir_fichas=True,
            hoy="2026-11-18",
            adaptadores_registry={"yamaha": FakeYamaha},
            robots_guard=robots_guard.RobotsGuard(fetcher=lambda _: (200, "User-agent: *\nAllow: /\n")),
        )
        self.assertEqual([], alertas)
        self.assertTrue(docs["yamaha"]["modelos"][0]["tiene_ficha"])
        snap = json.loads(ruta_snapshot.read_text(encoding="utf-8"))
        self.assertTrue(snap["Yamaha"][0]["tiene_ficha"])
        archivos = list(ruta_fichas.glob("yamaha_fz25-abs_249_*.json"))
        self.assertEqual(1, len(archivos))
        ficha_guardada = json.loads(archivos[0].read_text(encoding="utf-8"))
        self.assertEqual("fuente" if modelo.anio_fuente else "deteccion", ficha_guardada["anio_origen"])


if __name__ == "__main__":
    unittest.main()
