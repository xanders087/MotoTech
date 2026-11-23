"""Pruebas unitarias offline del recolector oficial (sin llamadas de red reales)."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

CARPETA_RECOLECTOR = Path(__file__).resolve().parent
if str(CARPETA_RECOLECTOR) not in sys.path:
    sys.path.insert(0, str(CARPETA_RECOLECTOR))

import adaptadores  # noqa: E402
import recolectar  # noqa: E402
import robots_guard  # noqa: E402


class WhitelistYRobotsTest(unittest.TestCase):
    def test_es_url_oficial_solo_https_y_dominios_permitidos(self):
        doms = ["incolmotos-yamaha.com.co", "motos.honda.com.co"]
        self.assertTrue(robots_guard.es_url_oficial("https://www.incolmotos-yamaha.com.co/vehiculos/", doms))
        self.assertTrue(robots_guard.es_url_oficial("https://motos.honda.com.co/motos-honda", doms))
        self.assertFalse(robots_guard.es_url_oficial("http://www.incolmotos-yamaha.com.co/vehiculos/", doms))
        self.assertFalse(robots_guard.es_url_oficial("https://evil-yamaha.com.co/vehiculos/", doms))
        self.assertFalse(robots_guard.es_url_oficial("https://wikipedia.org/wiki/Yamaha", doms))
        self.assertFalse(robots_guard.es_url_oficial("", doms))

    def test_www_removeprefix_no_corrompe_dominios_con_w(self):
        self.assertTrue(robots_guard.es_url_oficial("https://worldmotos.com/a", ["worldmotos.com"]))
        self.assertFalse(robots_guard.es_url_oficial("https://orldmotos.com/a", ["worldmotos.com"]))

    def test_robots_guard_respeta_disallow_y_errores(self):
        def fake_fetch(url: str):
            if "ejemplo.com" in url:
                return 200, "User-agent: *\nDisallow: /distribuidor/\nAllow: /vehiculos/\n"
            if "sinrobots.com" in url:
                return 404, ""
            if "bloqueado.com" in url:
                return 403, ""
            return 0, ""

        guard = robots_guard.RobotsGuard(fetcher=fake_fetch)
        self.assertTrue(guard.puede_consultar("https://ejemplo.com/vehiculos/"))
        self.assertFalse(guard.puede_consultar("https://ejemplo.com/distribuidor/panel"))
        self.assertTrue(guard.puede_consultar("https://sinrobots.com/motos"))
        self.assertFalse(guard.puede_consultar("https://bloqueado.com/motos"))
        self.assertFalse(guard.puede_consultar("https://caido.com/motos"))

    def test_robots_guard_fallback_cuando_waf_bloquea_urllib_con_403(self):
        llamadas_fallback: list[str] = []

        def primary_fetch(url: str):
            return 403, ""

        def fallback_fetch(url: str):
            llamadas_fallback.append(url)
            if "aktmotos.com" in url:
                return 200, "User-agent: *\nDisallow: /wp-admin/\nAllow: /wp-admin/admin-ajax.php\n"
            return 403, ""

        guard = robots_guard.RobotsGuard(fetcher=primary_fetch, fallback_fetcher=fallback_fetch)
        self.assertTrue(guard.puede_consultar("https://aktmotos.com/motos-akt/"))
        self.assertFalse(guard.puede_consultar("https://aktmotos.com/wp-admin/"))
        # Cacheado tras la primera consulta
        self.assertEqual(["https://aktmotos.com/robots.txt"], llamadas_fallback)
        # Si el fallback también devuelve 403, mantiene bloqueo total
        self.assertFalse(guard.puede_consultar("https://prohibido.com/motos/"))


class ParseoCcTest(unittest.TestCase):
    def test_parsea_formatos_oficiales(self):
        self.assertEqual(109, adaptadores.parsear_cc("109,19 cm³"))
        self.assertEqual(199, adaptadores.parsear_cc("199.4 cc"))
        self.assertEqual(249, adaptadores.parsear_cc("249 cc"))
        self.assertEqual(124, adaptadores.parsear_cc("124 cm3"))
        self.assertIsNone(adaptadores.parsear_cc("sin dato"))
        self.assertIsNone(adaptadores.parsear_cc("15 cc"))
        self.assertIsNone(adaptadores.parsear_cc("5000 cc"))


class AdaptadoresParsingTest(unittest.TestCase):
    def test_yamaha_sitemap_y_api_y_html_y_filtro_cuatrimoto(self):
        xml = """<?xml version="1.0"?>
        <urlset>
          <url><loc>https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/</loc></url>
          <url><loc>https://www.incolmotos-yamaha.com.co/vehiculo/yfm700r/2609/</loc></url>
          <url><loc>https://otro.com/vehiculo/fake/1/</loc></url>
        </urlset>"""
        urls = adaptadores.parsear_yamaha_sitemap(xml)
        self.assertEqual(["https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/"], urls)

        api_json = {
            "post_title": "FZ25 ABS",
            "detail": {
                "tipo_de_vehiculo": "Urbanas, trabajo y deportivas",
                "cilindraje_especificaciones": "249 cc",
                "manual": [
                    {
                        "nombre_manual": "Catálogo de partes FZ 25",
                        "manual_en_pdf": {"url": "https://www.incolmotos-yamaha.com.co/wp-content/uploads/partes.pdf"},
                    },
                    {
                        "nombre_manual": "Manual de propietario y garantías FZ25",
                        "manual_en_pdf": {"url": "https://www.incolmotos-yamaha.com.co/wp-content/uploads/B8V2.pdf"},
                    },
                ],
            },
        }
        m = adaptadores.parsear_yamaha_vehiculo_api(
            api_json, "https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/"
        )
        self.assertIsNotNone(m)
        self.assertEqual("FZ25 ABS", m.modelo)
        self.assertEqual(249, m.cilindraje_cc)
        self.assertEqual("https://www.incolmotos-yamaha.com.co/wp-content/uploads/B8V2.pdf", m.url_manual)

        html = """<html><body>
          <h1>FZ15</h1>
          <a href="/categoria/urbanas/">Urbanas</a>
          <div>Cilindraje 149 cc</div>
        </body></html>"""
        m_html = adaptadores.parsear_yamaha_vehiculo_html(
            html, "https://www.incolmotos-yamaha.com.co/vehiculo/fz15s/2176/"
        )
        self.assertIsNotNone(m_html)
        self.assertEqual("FZ15", m_html.modelo)
        self.assertEqual(149, m_html.cilindraje_cc)
        self.assertEqual("Urbanas", m_html.categoria)

    def test_honda_nextjs_flight_excluye_cuatrimotos(self):
        flight_payload = json.dumps({
            "bikes": [
                {
                    "name": "Navi",
                    "pdpUrl": "/motos-honda/NAVI/moto-navi",
                    "displacement": "109,19 cm³",
                    "category": "Navi",
                    "manualUrl": "https://d3lewjhzzgclom.cloudfront.net/sites/default/files/navi.pdf",
                },
                {
                    "name": "TRX420TM",
                    "pdpUrl": "/motos-honda/cuatrimotos/trx420",
                    "displacement": "420 cm³",
                    "category": "Cuatrimotos",
                    "manualUrl": "",
                },
            ]
        })
        script_arg = json.dumps("1:" + flight_payload)
        html = f"<html><body><script>self.__next_f.push([1,{script_arg}])</script></body></html>"
        modelos = adaptadores.parsear_honda_catalogo_html(html)
        self.assertEqual(1, len(modelos))
        self.assertEqual("Navi", modelos[0].modelo)
        self.assertEqual(109, modelos[0].cilindraje_cc)
        self.assertEqual("https://motos.honda.com.co/motos-honda/NAVI/moto-navi", modelos[0].url_fuente)
        self.assertEqual("https://d3lewjhzzgclom.cloudfront.net/sites/default/files/navi.pdf", modelos[0].url_manual)

    def test_bajaj_ignora_cilindraje_de_comparador_competencia(self):
        listado = """<html><body>
          <a href="/colombia/motos/pulsar/pulsar-ns200-fi-abs/">NS200</a>
          <a href="/colombia/motos/motocarros/torito-re/">Torito</a>
          <a href="/colombia/motos/pulsar/">Categoria Pulsar</a>
        </body></html>"""
        links = adaptadores.parsear_bajaj_catalogo_links(listado)
        self.assertEqual(["https://grupouma.com/colombia/motos/pulsar/pulsar-ns200-fi-abs/"], links)

        detalle = """<html><body>
          <h1>Pulsar NS200 FI ABS DC</h1>
          <p>Cilindraje: 199.4 cc Potencia: 24.16 Hp</p>
          <a href="/colombia/wp-content/uploads/sites/2/2025/03/MU-Pulsar-200-NS-FI-ABS-2026.pdf">Manual</a>
          <section>
            <h2>Descubre por qué somos mejores</h2>
            <p>Competidor HONDA CBR 190R Cilindraje: 184.4 cc</p>
          </section>
        </body></html>"""
        m = adaptadores.parsear_bajaj_modelo_html(
            detalle, "https://grupouma.com/colombia/motos/pulsar/pulsar-ns200-fi-abs/"
        )
        self.assertIsNotNone(m)
        self.assertEqual(199, m.cilindraje_cc)
        self.assertIn("MU-Pulsar-200-NS-FI-ABS-2026.pdf", m.url_manual or "")

    def test_akt_extrae_titulo_cc_y_manual(self):
        listado = """<html><body>
          <a href="/motos-akt/calle/nueva-nkd-125/">NKD 125</a>
          <a href="/motos-akt/calle/">Calle</a>
        </body></html>"""
        links = adaptadores.parsear_akt_catalogo_links(listado)
        self.assertEqual(["https://aktmotos.com/motos-akt/calle/nueva-nkd-125/"], links)

        detalle = """<html><head><title>NUEVA NKD 125 - AKT Motos</title></head><body>
          <a href="https://aktmotos.com/wp-content/uploads/2026/09/Manual-de-usuario-NKD-Baja2026-1.pdf">Ver manual</a>
          <div>Motor 124 cc Cilindraje CDI Encendido</div>
        </body></html>"""
        m = adaptadores.parsear_akt_modelo_html(
            detalle, "https://aktmotos.com/motos-akt/calle/nueva-nkd-125/"
        )
        self.assertIsNotNone(m)
        self.assertEqual("NUEVA NKD 125", m.modelo)
        self.assertEqual(124, m.cilindraje_cc)
        self.assertEqual("Calle", m.categoria)
        self.assertIn("Manual-de-usuario-NKD", m.url_manual or "")

        detalle_seo = """<html><body>
          <h1>Nueva NKD 125: precio y ficha técnica</h1>
          <div>Motor 124 cc Cilindraje</div>
        </body></html>"""
        m_seo = adaptadores.parsear_akt_modelo_html(
            detalle_seo, "https://aktmotos.com/motos-akt/calle/nueva-nkd-125/"
        )
        self.assertIsNotNone(m_seo)
        self.assertEqual("Nueva NKD 125", m_seo.modelo)

    def test_suzuki_sitemap_y_filtro_no_moto(self):
        xml = """<?xml version="1.0"?>
        <urlset>
          <url><loc>https://suzuki.com.co/motocicletas/lista/sport</loc></url>
          <url><loc>https://suzuki.com.co/motocicletas/sport/gixxer-sf-250</loc></url>
          <url><loc>https://suzuki.com.co/motocicletas/atv/kingquad-400</loc></url>
          <url><loc>https://otro.com/motocicletas/sport/fake</loc></url>
        </urlset>"""
        urls = adaptadores.parsear_suzuki_sitemap(xml)
        self.assertEqual(["https://suzuki.com.co/motocicletas/sport/gixxer-sf-250"], urls)

        html_sin_cc = "<html><body><h1>Burgman Street 125</h1><p>Próximamente</p></body></html>"
        self.assertIsNone(
            adaptadores.parsear_suzuki_modelo_html(
                html_sin_cc, "https://www.suzuki.com.co/motocicletas/automaticas/burgman-street-125"
            )
        )

    def test_hero_sitemap_excluye_promos_duplicadas(self):
        xml = """<?xml version="1.0"?>
        <urlset>
          <url><loc><![CDATA[https://heromotos.com.co/trabajo/eco-100/]]></loc></url>
          <url><loc><![CDATA[https://heromotos.com.co/trabajo/eco-100-nuevo-precio/]]></loc></url>
          <url><loc><![CDATA[https://heromotos.com.co/plan-de-mantenimiento/]]></loc></url>
        </urlset>"""
        urls = adaptadores.parsear_hero_sitemap(xml)
        self.assertEqual(["https://heromotos.com.co/trabajo/eco-100/"], urls)

    def test_auteco_vtex_excluye_motocarros_electricas_y_utilitarios(self):
        motocarro = {
            "brand": "TVS",
            "productName": "MOTOCARRO TVS KING DELUXE PLUS GS+",
            "link": "https://www.auteco.com.co/motocarro-tvs-king/p",
            "categories": ["/Vehículos de carga/Motocarros/", "/Motos/"],
            "CILINDRAJE": ["199.26 cc"],
            "allSpecifications": ["CILINDRAJE"],
        }
        electrica = {
            "brand": "TVS",
            "productName": "MOTO ELECTRICA TVS IQUBE",
            "link": "https://www.auteco.com.co/moto-electrica-tvs-iqube/p",
            "categories": ["/Motos Eléctricas/Scooter/", "/Motos/"],
            "allSpecifications": [],
        }
        utilitario = {
            "brand": "Kawasaki",
            "productName": "KAWASAKI BRUTE FORCE 450",
            "link": "https://www.auteco.com.co/kawasaki-brute-force-450/p",
            "categories": ["/Otros vehículos/Utilitarios Mule/", "/Motos/"],
            "CILINDRAJE": ["443cc"],
            "allSpecifications": ["CILINDRAJE"],
        }
        self.assertIsNone(adaptadores.parsear_auteco_vtex_producto(motocarro, "TVS"))
        self.assertIsNone(adaptadores.parsear_auteco_vtex_producto(electrica, "TVS"))
        self.assertIsNone(adaptadores.parsear_auteco_vtex_producto(utilitario, "Kawasaki"))

    def test_autocolombiana_vtex_extrae_anio_y_rechaza_torque_corrupto(self):
        prod = {
            "brand": "Husqvarna",
            "productName": "HUSQVARNA SVARTPILEN 801 2026",
            "link": "https://www.autocolombiana.com.co/husqvarna-svartpilen-801-2026/p",
            "categories": ["/Husqvarna/MODELOS/NAKED/", "/Husqvarna/MODELOS/"],
            "AÑO MODELO": ["2026"],
            "CILINDRAJE": ["799 cm³"],
            "POTENCIA": ["105 HP @9,250 rpm"],
            "TORQUE": ["Capacidad útil del depósito: 13.7 litros"],
        }
        m = adaptadores.parsear_autocolombiana_vtex_producto(prod, "Husqvarna", hoy="2026-11-18")
        self.assertIsNotNone(m)
        self.assertEqual("SVARTPILEN 801", m.modelo)
        self.assertEqual(799, m.cilindraje_cc)
        self.assertEqual(2026, m.anio_fuente)
        self.assertEqual("Naked", m.categoria)
        self.assertIn("cilindraje", m.ficha_tecnica)
        self.assertIn("potencia_maxima", m.ficha_tecnica)
        self.assertNotIn("torque_maximo", m.ficha_tecnica)


class RecoleccionYRespaldoTest(unittest.TestCase):
    def test_conserva_descontinuados_cruza_fichas_y_protege_contra_cero_modelos(self):
        tmp = Path(tempfile.mkdtemp())
        ruta_fuentes = tmp / "fuentes.json"
        ruta_snapshot = tmp / "catalog.json"
        ruta_fichas = tmp / "fichas"
        ruta_fichas.mkdir()

        ruta_fuentes.write_text(json.dumps({
            "Yamaha": {"dominios": ["incolmotos-yamaha.com.co"], "catalogo": ["https://www.incolmotos-yamaha.com.co/vehiculos/"], "adaptador": "yamaha"},
            "Honda": {"dominios": ["motos.honda.com.co"], "catalogo": ["https://motos.honda.com.co/motos-honda"], "adaptador": "honda"},
        }), encoding="utf-8")

        ruta_snapshot.write_text(json.dumps({
            "Yamaha": [
                {"modelo": "FZ25 ABS", "cilindraje": "249cc", "categoria": "Naked"},
                {"modelo": "XTZ 125 Antigua", "cilindraje": "124cc", "categoria": "Doble Propósito"},
            ],
            "Honda": [
                {"modelo": "Navi", "cilindraje": "109cc", "categoria": "Scooter"},
            ],
            "Suzuki": [
                {"modelo": "GN 125", "cilindraje": "124cc", "categoria": "Urbana"},
            ],
        }), encoding="utf-8")

        # Crear una ficha válida para Yamaha FZ25 ABS 249cc
        (ruta_fichas / "yamaha_fz25-abs_249_2024-2025.json").write_text(json.dumps({
            "marca": "Yamaha",
            "modelo": "FZ25 ABS",
            "cilindraje_cc": 249,
            "anio_desde": 2024,
            "anio_hasta": 2025,
            "manual": {"titulo": "Manual FZ25", "edicion": "1a", "url": "https://www.incolmotos-yamaha.com.co/m.pdf"},
            "revision": {"revisado_por": "Prueba", "fecha": "2026-11-01"},
            "ficha_tecnica": {"capacidad_aceite": {"valor": "1.3 L", "pagina": 10}},
            "plan_mantenimiento": [],
        }), encoding="utf-8")

        class FakeYamaha:
            def __init__(self, marca, cfg, client): pass
            def recolectar(self):
                return [
                    adaptadores.ModeloOficial("FZ25 ABS", 249, "Street", "https://www.incolmotos-yamaha.com.co/vehiculo/fz25/1/"),
                ]

        class FakeHondaVacio:
            def __init__(self, marca, cfg, client): pass
            def recolectar(self):
                return []  # Simula fallo de sitio -> no debe borrar Honda!

        docs, alertas = recolectar.ejecutar_recoleccion(
            ruta_fuentes=ruta_fuentes,
            ruta_snapshot=ruta_snapshot,
            ruta_fichas=ruta_fichas,
            simular=True,
            escribir_snapshot=True,
            hoy="2026-11-17",
            adaptadores_registry={"yamaha": FakeYamaha, "honda": FakeHondaVacio},
            robots_guard=robots_guard.RobotsGuard(fetcher=lambda _: (200, "User-agent: *\nAllow: /\n")),
        )

        # Honda generó alerta y NO se publicó ni borró su snapshot
        self.assertEqual(1, len(alertas))
        self.assertIn("Honda", alertas[0])
        self.assertIn("yamaha", docs)
        self.assertEqual("publicada", docs["yamaha"]["estado"])
        self.assertNotIn("honda", docs)

        modelos_yamaha = {m["modelo"]: m for m in docs["yamaha"]["modelos"]}
        self.assertTrue(modelos_yamaha["FZ25 ABS"]["vigente"])
        self.assertTrue(modelos_yamaha["FZ25 ABS"]["tiene_ficha"])
        self.assertFalse(modelos_yamaha["XTZ 125 Antigua"]["vigente"])
        self.assertFalse(modelos_yamaha["XTZ 125 Antigua"]["tiene_ficha"])

        snap = json.loads(ruta_snapshot.read_text(encoding="utf-8"))
        # Honda y Suzuki deben permanecer intactos en el snapshot offline
        self.assertEqual("Navi", snap["Honda"][0]["modelo"])
        self.assertEqual("GN 125", snap["Suzuki"][0]["modelo"])
        self.assertEqual(2, len(snap["Yamaha"]))


if __name__ == "__main__":
    unittest.main()
