package com.example

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// =========================================================================
// DTOs para APIs públicas de motocicletas (NHTSA vPIC, MediaWiki, Wikidata)
// =========================================================================

@JsonClass(generateAdapter = true)
data class NhtsaModelsResponse(
    @Json(name = "Count") val count: Int? = null,
    @Json(name = "Results") val results: List<NhtsaModelItem>? = null
)

@JsonClass(generateAdapter = true)
data class NhtsaModelItem(
    @Json(name = "Make_Name") val makeName: String? = null,
    @Json(name = "Model_Name") val modelName: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaWikiParseResponse(
    val parse: MediaWikiParseContent? = null
)

@JsonClass(generateAdapter = true)
data class MediaWikiParseContent(
    val title: String? = null,
    val text: Map<String, String>? = null
)

/**
 * Resultado consolidado del servicio Retrofit de búsqueda de modelos en tiempo real.
 */
data class RealTimeBrandCatalogResult(
    val marca: String,
    val modelos: List<MotoModelSpec>,
    val fuentesExitosas: List<String>
)

/**
 * Interfaz Retrofit dedicada a consultar APIs públicas de datos de motocicletas
 * y ejecutar web scraping controlado sobre catálogos oficiales y tablas técnicas.
 */
interface MotorcycleCatalogApi {

    /**
     * 1. API Pública NHTSA vPIC (US DOT Vehicle Product Information Catalog):
     * Obtiene modelos registrados de tipo motocicleta para una marca dada.
     */
    @Headers("User-Agent: MotoTechCockpit/1.0 (Android; Retrofit)")
    @GET("https://vpic.nhtsa.dot.gov/api/vehicles/GetModelsForMakeYear/make/{make}/vehicletype/motorcycle?format=json")
    suspend fun getNhtsaMotorcycleModels(
        @Path("make") make: String
    ): NhtsaModelsResponse

    /**
     * 2. API Pública MediaWiki / Wikipedia (Tablas estructuradas de modelos y cilindradas por marca):
     * Obtiene el HTML estructurado de anexos/listas de motocicletas por fabricante.
     */
    @Headers("User-Agent: MotoTechCockpit/1.0 (Android; Retrofit)")
    @GET("https://en.wikipedia.org/w/api.php?action=parse&prop=text&format=json&redirects=1")
    suspend fun getWikipediaBrandCatalogHtml(
        @Query("page") pageTitle: String
    ): MediaWikiParseResponse

    /**
     * 3. Web Scraping Controlado vía Retrofit (@GET con @Url dinámico):
     * Descarga el HTML de portales oficiales de fabricantes y directorios técnicos de motos
     * para extraer modelos y cilindradas reales mediante análisis controlado del DOM.
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language: es-CO,es;q=0.9,en;q=0.8"
    )
    @GET
    suspend fun scrapeCatalogWebPage(
        @Url url: String
    ): ResponseBody
}

/**
 * Cliente Retrofit y motor de extracción/scraping controlado de modelos y cilindradas por marca.
 */
object MotorcycleCatalogClient {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val api: MotorcycleCatalogApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://vpic.nhtsa.dot.gov/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MotorcycleCatalogApi::class.java)
    }

    /**
     * URLs de catálogos oficiales de marcas comercializadas en Colombia y Latinoamérica
     * para web scraping controlado con Retrofit.
     */
    internal val OFFICIAL_BRAND_URLS: Map<String, List<String>> = mapOf(
        "Yamaha" to listOf("https://www.incolmotos-yamaha.com.co/vehiculos/"),
        "Bajaj" to listOf("https://grupouma.com/colombia/motos/"),
        "AKT" to listOf("https://www.aktmotos.com/motos"),
        "Suzuki" to listOf("https://suzuki.com.co/motocicletas"),
        "Honda" to listOf("https://motos.honda.com.co/motos-honda"),
        "KTM" to listOf("https://www.auteco.com.co/motos-ktm/"),
        "TVS" to listOf("https://www.auteco.com.co/motos-tvs/"),
        "Hero" to listOf("https://heromotos.com.co/motos/"),
        "Victory" to listOf("https://www.autecomobility.com/motos-victory"),
        "Kawasaki" to listOf("https://www.auteco.com.co/motos-kawasaki/"),
        "Royal Enfield" to listOf("https://royalenfieldco.com/motos/"),
        "BMW" to listOf("https://www.bmw-motorrad.co/es/models/modeloverview.html"),
        "Benelli" to listOf("https://benelli.com/co-es/products/motos"),
        "Husqvarna" to listOf("https://www.auteco.com.co/motos-husqvarna/"),
        "CFMoto" to listOf("https://cfmoto.com.co/motos/"),
        "Ducati" to listOf("https://www.ducaticolombia.co/motos/"),
        "Triumph" to listOf("https://www.triumphmotorcycles.co/motocicletas"),
        "Kymco" to listOf("https://www.autecomobility.com/motos-kymco"),
        "SYM" to listOf("https://symcolombia.com/motos/")
    )

    internal val WIKIPEDIA_BRAND_PAGES: Map<String, String> = mapOf(
        "Yamaha" to "List_of_Yamaha_motorcycles",
        "Honda" to "List_of_Honda_motorcycles",
        "Suzuki" to "List_of_Suzuki_motorcycles",
        "Kawasaki" to "List_of_Kawasaki_motorcycles",
        "KTM" to "List_of_KTM_motorcycles",
        "BMW" to "List_of_BMW_motorcycles",
        "Ducati" to "List_of_Ducati_motorcycles",
        "Triumph" to "List_of_Triumph_motorcycles",
        "Royal Enfield" to "List_of_Royal_Enfield_motorcycles",
        "Benelli" to "List_of_Benelli_motorcycles",
        "Bajaj" to "Bajaj_Pulsar",
        "TVS" to "TVS_Apache",
        "Hero" to "Hero_MotoCorp",
        "Husqvarna" to "Husqvarna_Motorcycles",
        "CFMoto" to "CFMoto",
        "Kymco" to "Kymco",
        "SYM" to "SYM_Motors"
    )

    /**
     * Consulta en paralelo mediante Retrofit:
     * 1) API Pública NHTSA vPIC
     * 2) API MediaWiki / Tablas técnicas de modelos por marca
     * 3) Web Scraping controlado sobre el portal del fabricante
     */
    suspend fun fetchBrandCatalogRealTime(
        marca: String,
        service: MotorcycleCatalogApi = api
    ): RealTimeBrandCatalogResult = coroutineScope {
        val cleanMarca = marca.trim()
        val fuentes = mutableListOf<String>()

        val nhtsaDeferred = async {
            runCatching {
                val resp = service.getNhtsaMotorcycleModels(cleanMarca)
                val parsed = MotorcycleWebScraper.parseNhtsaModels(cleanMarca, resp)
                if (parsed.isNotEmpty()) synchronized(fuentes) { fuentes.add("API NHTSA vPIC") }
                parsed
            }.getOrDefault(emptyList())
        }

        val wikiDeferred = async {
            val wikiPage = WIKIPEDIA_BRAND_PAGES.entries
                .firstOrNull { it.key.equals(cleanMarca, ignoreCase = true) }
                ?.value ?: "List_of_${cleanMarca.replace(' ', '_')}_motorcycles"
            runCatching {
                val resp = service.getWikipediaBrandCatalogHtml(wikiPage)
                val html = resp.parse?.text?.get("*").orEmpty()
                val parsed = MotorcycleWebScraper.scrapeHtmlForModels(cleanMarca, html)
                if (parsed.isNotEmpty()) synchronized(fuentes) { fuentes.add("Catálogo Técnico Wikipedia API") }
                parsed
            }.getOrDefault(emptyList())
        }

        val scrapeDeferred = async {
            val urls = OFFICIAL_BRAND_URLS.entries
                .firstOrNull { it.key.equals(cleanMarca, ignoreCase = true) }
                ?.value.orEmpty()
            val collected = mutableListOf<MotoModelSpec>()
            for (url in urls) {
                val items = runCatching {
                    service.scrapeCatalogWebPage(url).use { body ->
                        val html = body.string()
                        MotorcycleWebScraper.scrapeHtmlForModels(cleanMarca, html)
                    }
                }.getOrDefault(emptyList())
                if (items.isNotEmpty()) {
                    collected.addAll(items)
                    synchronized(fuentes) { fuentes.add("Scraping Portal Oficial $cleanMarca") }
                }
            }
            collected
        }

        val allOnline = buildList {
            addAll(scrapeDeferred.await())
            addAll(wikiDeferred.await())
            addAll(nhtsaDeferred.await())
        }

        val deduplicated = MotorcycleCatalog.mergeModels(emptyList(), allOnline)
        RealTimeBrandCatalogResult(
            marca = cleanMarca,
            modelos = deduplicated,
            fuentesExitosas = fuentes.distinct()
        )
    }
}

/**
 * Analizador determinista para Web Scraping controlado de HTML/JSON-LD y normalización de cilindradas.
 */
object MotorcycleWebScraper {

    // Detecta cilindrada explícita: "689 cc", "155cc", "199.5 cm³", "249 cm3"
    private val CC_EXPLICIT_REGEX = Regex(
        pattern = """\b(\d{2,4})(?:[.,]\d+)?\s*(?:cc\b|cm³|cm3\b|c\.c\.)""",
        option = RegexOption.IGNORE_CASE
    )

    // Detecta filas de tablas HTML <tr>...</tr>
    private val TABLE_ROW_REGEX = Regex(
        pattern = """<tr[^>]*>(.*?)</tr>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Detecta celdas <td> o <th>
    private val TABLE_CELL_REGEX = Regex(
        pattern = """<t[dh][^>]*>(.*?)</t[dh]>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Detecta bloques de tarjetas de producto o artículos en sitios oficiales
    private val CARD_BLOCK_REGEX = Regex(
        pattern = """<(?:article|div|li)[^>]*class=["'][^"']*(?:moto|model|product|vehicle|card|item)[^"']*["'][^>]*>(.*?)</(?:article|div|li)>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Detecta encabezados dentro de tarjetas HTML (h2, h3, h4)
    private val HEADING_REGEX = Regex(
        pattern = """<h[2-4][^>]*>(.*?)</h[2-4]>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Detecta cilindrada implícita en el nombre de un modelo de NHTSA (ej. "MT-07", "CB500F", "CBR600RR", "Duke 390")
    private val MODEL_NUMBER_DISPLACEMENT_REGEX = Regex(
        pattern = """(?<![0-9])(1800|1390|1300|1290|1250|1200|1190|1100|1050|1000|990|959|950|937|901|900|890|850|800|790|765|750|701|700|690|675|660|650|636|600|560|530|502|500|450|411|401|400|390|350|320|310|300|250|230|225|220|200|190|180|175|165|160|155|150|135|125|115|110|100|90|80|70|50)(?![0-9])"""
    )

    private val KNOWN_DISPLACEMENT_OVERRIDES: Map<String, String> = mapOf(
        "mt-07" to "689cc",
        "mt07" to "689cc",
        "fz-07" to "689cc",
        "yzf-r7" to "689cc",
        "tenere 700" to "689cc",
        "ténéré 700" to "689cc",
        "mt-09" to "890cc",
        "mt09" to "890cc",
        "tracer 9" to "890cc",
        "mt-03" to "321cc",
        "yzf-r3" to "321cc",
        "mt-15" to "155cc",
        "yzf-r15" to "155cc",
        "nmax" to "155cc",
        "aerox 155" to "155cc",
        "crypton" to "114cc",
        "xtz 125" to "124cc",
        "xtz 150" to "149cc",
        "xtz 250" to "249cc",
        "fz25" to "249cc",
        "pulsar ns 200" to "199cc",
        "pulsar ns200" to "199cc",
        "pulsar rs 200" to "199cc",
        "dominar 400" to "373cc",
        "dominar 250" to "248cc",
        "boxer ct 100" to "102cc",
        "nkd 125" to "124cc",
        "duke 390" to "373cc",
        "390 duke" to "373cc",
        "rc 390" to "373cc",
        "390 adventure" to "373cc",
        "duke 200" to "199cc",
        "200 duke" to "199cc",
        "g 310 gs" to "313cc",
        "g 310 r" to "313cc",
        "g310gs" to "313cc",
        "g310r" to "313cc",
        "f 750 gs" to "853cc",
        "f 850 gs" to "853cc",
        "f 900 gs" to "895cc",
        "r 1250 gs" to "1254cc",
        "cb190r" to "184cc",
        "cb 190r" to "184cc",
        "xr 190l" to "184cc",
        "xre 300" to "291cc",
        "cb 500f" to "471cc",
        "cb500f" to "471cc",
        "cb 500x" to "471cc",
        "cb500x" to "471cc",
        "nx 500" to "471cc",
        "gixxer 150" to "155cc",
        "gixxer 250" to "249cc",
        "v-strom 250" to "249cc",
        "v-strom 650" to "645cc",
        "dr 650" to "644cc",
        "xpulse 200" to "199cc",
        "apache rtr 200" to "197cc",
        "apache rtr 160" to "159cc",
        "himalayan 411" to "411cc",
        "himalayan 450" to "452cc",
        "hunter 350" to "349cc",
        "classic 350" to "349cc",
        "meteor 350" to "349cc",
        "interceptor 650" to "648cc",
        "continental gt 650" to "648cc"
    )

    /**
     * Extrae modelos y cilindradas desde el HTML obtenido por Retrofit (tablas técnicas y tarjetas de modelos).
     */
    fun scrapeHtmlForModels(marca: String, html: String): List<MotoModelSpec> {
        if (html.isBlank()) return emptyList()
        val results = mutableListOf<MotoModelSpec>()

        // 1. Extracción desde tablas HTML (<tr> con celdas de modelo y cilindrada en cc/cm³)
        for (rowMatch in TABLE_ROW_REGEX.findAll(html)) {
            val rowHtml = rowMatch.groupValues[1]
            val cells = TABLE_CELL_REGEX.findAll(rowHtml)
                .map { cleanHtmlText(it.groupValues[1]) }
                .filter { it.isNotEmpty() }
                .toList()

            if (cells.size >= 2) {
                val fullRowText = cells.joinToString(" | ")
                val ccMatch = CC_EXPLICIT_REGEX.find(fullRowText)
                val firstCell = cleanModelName(marca, cells[0])

                if (isValidModelCandidate(firstCell)) {
                    val ccValue = ccMatch?.groupValues?.get(1)?.let { "${it}cc" }
                        ?: inferDisplacementFromModelName(marca, firstCell)

                    if (ccValue != null) {
                        val category = inferCategoryFromText(fullRowText)
                        results.add(MotoModelSpec(modelo = firstCell, cilindraje = ccValue, categoria = category))
                    }
                }
            }
        }

        // 2. Extracción desde tarjetas de catálogo web (<article>, <div class="...model...">)
        for (cardMatch in CARD_BLOCK_REGEX.findAll(html)) {
            val cardHtml = cardMatch.groupValues[1]
            val headingHtml = HEADING_REGEX.find(cardHtml)?.groupValues?.get(1) ?: continue
            val candidateName = cleanModelName(marca, cleanHtmlText(headingHtml))
            if (!isValidModelCandidate(candidateName)) continue

            val plainCard = cleanHtmlText(cardHtml)
            val explicitCc = CC_EXPLICIT_REGEX.find(plainCard)?.groupValues?.get(1)?.let { "${it}cc" }
            val resolvedCc = explicitCc ?: inferDisplacementFromModelName(marca, candidateName)

            if (resolvedCc != null) {
                val category = inferCategoryFromText(plainCard)
                results.add(
                    MotoModelSpec(
                        modelo = candidateName,
                        cilindraje = resolvedCc,
                        categoria = category
                    )
                )
            }
        }

        return MotorcycleCatalog.mergeModels(emptyList(), results)
    }

    /**
     * Procesa la respuesta JSON de la API NHTSA vPIC para extraer modelos de la marca y su cilindrada.
     */
    fun parseNhtsaModels(marca: String, response: NhtsaModelsResponse): List<MotoModelSpec> {
        val rawItems = response.results.orEmpty()
        val specs = rawItems.mapNotNull { item ->
            val rawModel = item.modelName?.trim().orEmpty()
            val cleaned = cleanModelName(marca, rawModel)
            if (!isValidModelCandidate(cleaned)) return@mapNotNull null

            val cc = inferDisplacementFromModelName(marca, cleaned) ?: return@mapNotNull null
            val category = inferCategoryFromText(cleaned)
            MotoModelSpec(modelo = cleaned, cilindraje = cc, categoria = category)
        }
        return MotorcycleCatalog.mergeModels(emptyList(), specs)
    }

    fun inferDisplacementFromModelName(marca: String, modelo: String): String? {
        val norm = modelo.trim().lowercase().replace(Regex("\\s+"), " ")
        KNOWN_DISPLACEMENT_OVERRIDES[norm]?.let { return it }
        KNOWN_DISPLACEMENT_OVERRIDES.entries.firstOrNull { norm.contains(it.key) }?.let { return it.value }

        MotorcycleCatalog.findDisplacement(marca, modelo)?.let { return it }

        val explicitMatch = CC_EXPLICIT_REGEX.find(modelo)
        if (explicitMatch != null) {
            return "${explicitMatch.groupValues[1]}cc"
        }

        val numberMatch = MODEL_NUMBER_DISPLACEMENT_REGEX.find(modelo)
        if (numberMatch != null) {
            val digits = numberMatch.groupValues[1].toIntOrNull()
            if (digits != null && digits in 50..2500) {
                return "${digits}cc"
            }
        }
        return null
    }

    private fun cleanHtmlText(rawHtml: String): String {
        return rawHtml
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<sup[^>]*>.*?</sup>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#160;", " ")
            .replace("&#8211;", "-")
            .replace(Regex("\\[\\d+\\]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanModelName(marca: String, rawName: String): String {
        var name = rawName.trim()
        if (name.startsWith(marca, ignoreCase = true)) {
            name = name.substring(marca.length).trim().removePrefix("-").trim()
        }
        // Limpiar paréntesis de años como "MT-07 (2014-present)"
        name = name.replace(Regex("\\(\\d{4}.*?\\)"), "").trim()
        return name.replace(Regex("\\s+"), " ")
    }

    private fun isValidModelCandidate(name: String): Boolean {
        if (name.length !in 2..38) return false
        val lower = name.lowercase()
        val blacklist = listOf(
            "model", "modelo", "year", "año", "engine", "motor", "displacement",
            "cilindrada", "category", "categoría", "type", "tipo", "notes", "notas",
            "image", "imagen", "production", "references", "total", "ver más", "comprar"
        )
        if (blacklist.any { lower == it || lower.startsWith("$it ") }) return false
        return name.any { it.isLetterOrDigit() }
    }

    private fun inferCategoryFromText(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("scooter") || lower.contains("nmax") || lower.contains("pcx") || lower.contains("bws") -> "Scooter"
            lower.contains("adventure") || lower.contains("touring") || lower.contains("v-strom") || lower.contains("versys") || lower.contains("gs") || lower.contains("tenere") || lower.contains("ténéré") || lower.contains("himalayan") -> "Adventure / Touring"
            lower.contains("enduro") || lower.contains("dual") || lower.contains("doble propósito") || lower.contains("xtz") || lower.contains("xr ") || lower.contains("xre") || lower.contains("dr ") || lower.contains("klx") || lower.contains("xpulse") || lower.contains("mrx") || lower.contains("ttr") -> "Doble Propósito / Enduro"
            lower.contains("supersport") || lower.contains("superbike") || lower.contains("sport") || lower.contains("ninja") || lower.contains("yzf-r") || lower.contains("panigale") || lower.contains("rc ") || lower.contains("gsx-r") || lower.contains("cbr") -> "Deportiva"
            lower.contains("cruiser") || lower.contains("chopper") || lower.contains("bobber") || lower.contains("vulcan") || lower.contains("meteor") -> "Cruiser / Clásica"
            lower.contains("naked") || lower.contains("street") || lower.contains("roadster") || lower.contains("mt-") || lower.contains("duke") || lower.contains("pulsar") || lower.contains("fz") || lower.contains("gixxer") || lower.contains("apache") || lower.contains("cb") -> "Naked / Street"
            else -> null
        }
    }
}
