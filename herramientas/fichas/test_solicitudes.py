"""Pruebas del listado de solicitudes. Los datos son ficticios."""
import unittest

import solicitudes


class FormatearTest(unittest.TestCase):
    def test_ordena_de_mas_a_menos_pedida(self):
        texto = solicitudes.formatear([
            {"marca": "Marca A", "modelo": "Uno", "cilindraje_cc": 100, "anio": 2020, "total": 2, "clave": "a"},
            {"marca": "Marca B", "modelo": "Dos", "cilindraje_cc": 200, "anio": 2021, "total": 7, "clave": "b"},
        ])
        lineas = texto.splitlines()
        self.assertIn("Marca B Dos 200cc 2021", lineas[1])
        self.assertTrue(lineas[1].strip().startswith("7"))
        self.assertIn("Marca A Uno 100cc 2020", lineas[2])

    def test_sin_solicitudes(self):
        self.assertEqual("No hay solicitudes.", solicitudes.formatear([]))

    def test_limite_y_datos_incompletos(self):
        datos = [{"total": i} for i in range(5)]
        texto = solicitudes.formatear(datos, limite=2)
        self.assertIn("… y 3 más.", texto)
        self.assertIn("? ? ?cc ?", texto)


if __name__ == "__main__":
    unittest.main()
