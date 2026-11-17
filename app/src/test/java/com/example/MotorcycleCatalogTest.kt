package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorcycleCatalogTest {

    @Test
    fun `todas las marcas tienen modelos verificados con cilindrada en cc`() {
        assertTrue(MotorcycleCatalog.MARCAS.isNotEmpty())
        for (marca in MotorcycleCatalog.MARCAS) {
            val modelos = MotorcycleCatalog.baseModelsForBrand(marca)
            assertFalse("La marca $marca debe tener modelos", modelos.isEmpty())
            for (spec in modelos) {
                assertTrue("El modelo de $marca no puede estar vacío", spec.modelo.isNotBlank())
                assertTrue(
                    "La cilindrada de ${spec.modelo} (${spec.cilindraje}) debe terminar en cc",
                    spec.cilindraje.endsWith("cc") && spec.cilindraje.any { it.isDigit() }
                )
            }
        }
    }

    @Test
    fun `cada marca devuelve exclusivamente sus propios modelos y calcula su cilindrada exacta`() {
        val yamahaModels = MotorcycleCatalog.baseModelsForBrand("Yamaha")
        assertTrue(yamahaModels.any { it.modelo == "MT-07" })
        assertFalse(yamahaModels.any { it.modelo == "Pulsar NS200" })
        assertEquals("689cc", MotorcycleCatalog.findDisplacement("Yamaha", "MT-07", yamahaModels))

        val bajajModels = MotorcycleCatalog.baseModelsForBrand("Bajaj")
        assertTrue(bajajModels.any { it.modelo == "Pulsar NS200" })
        assertFalse(bajajModels.any { it.modelo == "MT-07" })
        assertEquals("199cc", MotorcycleCatalog.findDisplacement("Bajaj", "Pulsar NS200", bajajModels))

        val aktModels = MotorcycleCatalog.baseModelsForBrand("AKT")
        assertTrue(aktModels.any { it.modelo == "NKD 125" })
        assertEquals("124cc", MotorcycleCatalog.findDisplacement("AKT", "NKD 125", aktModels))
    }

    @Test
    fun `normaliza cilindraje y resuelve marcas sin distinguir mayusculas`() {
        assertEquals("249cc", MotorcycleCatalog.normalizeCilindraje("249 cc"))
        assertEquals("125cc", MotorcycleCatalog.normalizeCilindraje("125"))
        assertTrue(MotorcycleCatalog.baseModelsForBrand("  yamaha ").isNotEmpty())
        assertTrue(MotorcycleCatalog.baseModelsForBrand("Marca Inexistente").isEmpty())
        assertEquals("689cc", MotorcycleCatalog.findDisplacement("yamaha", "  mt-07 "))
        assertNull(MotorcycleCatalog.findDisplacement("Yamaha", "Modelo Desconocido"))
    }

    @Test
    fun `parsea documentos remotos de catalogo y combina con respaldo offline`() {
        val docs = listOf(
            mapOf(
                "marca" to "Yamaha",
                "modelos" to listOf(
                    mapOf(
                        "modelo" to "FZ25 ABS",
                        "cilindraje_cc" to 249L,
                        "categoria" to "Naked",
                        "url_fuente" to "https://www.incolmotos-yamaha.com.co/vehiculo/fzn250a/9260/",
                        "tiene_ficha" to true,
                        "vigente" to true
                    ),
                    mapOf(
                        "modelo" to "XTZ 125 Antigua",
                        "cilindraje_cc" to 124,
                        "tiene_ficha" to false,
                        "vigente" to false
                    ),
                    mapOf("modelo" to "", "cilindraje_cc" to 150) // inválido: debe ignorarse
                )
            )
        )

        val remoto = MotorcycleCatalog.parseCatalogDocuments(docs)
        assertEquals(1, remoto.size)
        val yamaha = remoto["Yamaha"].orEmpty()
        assertEquals(2, yamaha.size)
        assertEquals("249cc", yamaha[0].cilindraje)
        assertTrue(yamaha[0].tiene_ficha)
        assertTrue(yamaha[0].vigente)
        assertFalse(yamaha[1].vigente)

        val combinado = MotorcycleCatalog.mergeWithOfflineCatalog(remoto)
        // Yamaha se actualiza con la lista remota; otras marcas se preservan desde motorcycle_catalog.json
        assertEquals(2, MotorcycleCatalog.modelsForBrand("Yamaha", combinado).size)
        assertTrue(MotorcycleCatalog.modelsForBrand("Suzuki", combinado).isNotEmpty())
        // Sin datos remotos conserva la instantánea completa sin conexión
        val soloOffline = MotorcycleCatalog.mergeWithOfflineCatalog(emptyMap())
        assertTrue(MotorcycleCatalog.modelsForBrand("Yamaha", soloOffline).size > 2)
    }
}
