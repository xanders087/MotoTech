package com.example

import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorcycleCatalogTest {

    private val adapter = Moshi.Builder().build().adapter(BrandCatalogResponse::class.java)

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
    fun `parsea respuesta en tiempo real de modelos y fusiona sin duplicar`() {
        val json = """
            ```json
            {
              "marca": "Yamaha",
              "modelos": [
                { "modelo": "MT-07", "cilindraje": "689 cc", "categoria": "Hyper Naked" },
                { "modelo": "Crypton 115 T115FI", "cilindraje": "114", "categoria": "Semiautomática" }
              ]
            }
            ```
        """.trimIndent()

        val online = MotorcycleCatalog.parseBrandCatalogJson(json, adapter)
        assertEquals(2, online.size)
        assertEquals("689cc", online[0].cilindraje)
        assertEquals("114cc", online[1].cilindraje)

        val base = MotorcycleCatalog.baseModelsForBrand("Yamaha")
        val merged = MotorcycleCatalog.mergeModels(base, online)
        assertEquals(base.size + 1, merged.size)
        assertEquals(1, merged.count { it.modelo.equals("MT-07", ignoreCase = true) })
    }

    @Test
    fun `web scraper controlado extrae modelos y cilindradas desde tablas y tarjetas HTML`() {
        val sampleHtml = """
            <html>
              <body>
                <table class="wikitable">
                  <tr><th>Model</th><th>Engine</th><th>Category</th></tr>
                  <tr><td>Yamaha XSR700</td><td>689 cc</td><td>Naked / Sport Heritage</td></tr>
                  <tr><td>Yamaha XSR900</td><td>890 cm³</td><td>Naked</td></tr>
                </table>
                <article class="product-card vehicle-item">
                  <h3>Yamaha WR155R</h3>
                  <p>Motor de 155 cc con refrigeración líquida para Enduro.</p>
                </article>
              </body>
            </html>
        """.trimIndent()

        val scraped = MotorcycleWebScraper.scrapeHtmlForModels("Yamaha", sampleHtml)
        assertEquals(3, scraped.size)
        assertEquals("XSR700", scraped[0].modelo)
        assertEquals("689cc", scraped[0].cilindraje)
        assertEquals("XSR900", scraped[1].modelo)
        assertEquals("890cc", scraped[1].cilindraje)
        assertEquals("WR155R", scraped[2].modelo)
        assertEquals("155cc", scraped[2].cilindraje)
    }

    @Test
    fun `parsea modelos desde la API NHTSA vPIC con inferencia exacta de cilindrada`() {
        val nhtsaResponse = NhtsaModelsResponse(
            count = 3,
            results = listOf(
                NhtsaModelItem(makeName = "KTM", modelName = "Duke 390"),
                NhtsaModelItem(makeName = "KTM", modelName = "890 Adventure R"),
                NhtsaModelItem(makeName = "KTM", modelName = "RC 200")
            )
        )

        val models = MotorcycleWebScraper.parseNhtsaModels("KTM", nhtsaResponse)
        assertEquals(3, models.size)
        assertEquals("373cc", models.first { it.modelo == "Duke 390" }.cilindraje)
        assertEquals("889cc", models.first { it.modelo == "890 Adventure R" }.cilindraje)
        assertEquals("199cc", models.first { it.modelo == "RC 200" }.cilindraje)
    }

    @Test
    fun `servicio Retrofit consolida datos de API NHTSA, Wikipedia API y Web Scraping`() = runTest {
        val fakeApi = object : MotorcycleCatalogApi {
            override suspend fun getNhtsaMotorcycleModels(make: String): NhtsaModelsResponse {
                return NhtsaModelsResponse(
                    count = 2,
                    results = listOf(
                        NhtsaModelItem(makeName = make, modelName = "CB500F"),
                        NhtsaModelItem(makeName = make, modelName = "CBR600RR")
                    )
                )
            }

            override suspend fun getWikipediaBrandCatalogHtml(pageTitle: String): MediaWikiParseResponse {
                val html = """
                    <table>
                      <tr><td>Honda Hornet 750</td><td>755 cc</td><td>Naked</td></tr>
                    </table>
                """.trimIndent()
                return MediaWikiParseResponse(
                    parse = MediaWikiParseContent(title = pageTitle, text = mapOf("*" to html))
                )
            }

            override suspend fun scrapeCatalogWebPage(url: String): ResponseBody {
                val html = """
                    <div class="moto-card">
                      <h3>Honda XR 300L Tornado</h3>
                      <span>293 cc Doble Propósito</span>
                    </div>
                """.trimIndent()
                return html.toResponseBody("text/html".toMediaType())
            }
        }

        val result = MotorcycleCatalogClient.fetchBrandCatalogRealTime("Honda", fakeApi)
        assertEquals("Honda", result.marca)
        assertEquals(4, result.modelos.size)
        assertTrue(result.modelos.any { it.modelo == "XR 300L Tornado" && it.cilindraje == "293cc" })
        assertTrue(result.modelos.any { it.modelo == "Hornet 750" && it.cilindraje == "755cc" })
        assertTrue(result.modelos.any { it.modelo == "CB500F" && it.cilindraje == "471cc" })
        assertTrue(result.modelos.any { it.modelo == "CBR600RR" && it.cilindraje == "600cc" })
        assertEquals(3, result.fuentesExitosas.size)
    }

    @Test
    fun `descubrimiento dinamico de modelos Gemini prioriza gemini-3_8-flash y excluye modelos no textuales`() {
        val rawModels = listOf(
            GeminiModelInfo("models/text-embedding-004", listOf("embedContent")),
            GeminiModelInfo("models/gemini-3.5-flash-lite", listOf("generateContent")),
            GeminiModelInfo("models/gemini-3.8-flash", listOf("generateContent", "countTokens")),
            GeminiModelInfo("models/imagen-3.0-generate-002", listOf("predict"))
        )

        val prioritized = RetrofitClient.filterAndPrioritizeModels(rawModels)
        assertEquals(listOf("gemini-3.8-flash", "gemini-3.5-flash-lite"), prioritized)
    }
}
