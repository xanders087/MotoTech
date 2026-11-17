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
}
