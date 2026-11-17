package com.example

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class MotoModelSpec(
    val modelo: String,
    val cilindraje: String,
    val categoria: String? = null
)

/**
 * Catálogo local de marcas y modelos para el selector mientras se activa el catálogo
 * oficial en Firestore (Fase 2). No realiza scraping ni infiere cilindradas.
 */
object MotorcycleCatalog {

    private val VERIFIED_CATALOG: Map<String, List<MotoModelSpec>> by lazy {
        loadCatalogFromResource()
    }

    val MARCAS: List<String> by lazy {
        VERIFIED_CATALOG.keys.toList()
    }

    private fun loadCatalogFromResource(): Map<String, List<MotoModelSpec>> {
        val stream = requireNotNull(
            MotorcycleCatalog::class.java.getResourceAsStream("/motorcycle_catalog.json")
        ) { "No se encontró el recurso /motorcycle_catalog.json" }
        val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val moshi = Moshi.Builder().build()
        val listType = Types.newParameterizedType(List::class.java, MotoModelSpec::class.java)
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, listType)
        val adapter: JsonAdapter<Map<String, List<MotoModelSpec>>> = moshi.adapter(mapType)
        return adapter.fromJson(json).orEmpty()
    }

    fun baseModelsForBrand(marca: String): List<MotoModelSpec> {
        val clean = marca.trim()
        val key = VERIFIED_CATALOG.keys.firstOrNull { it.equals(clean, ignoreCase = true) }
        return if (key != null) VERIFIED_CATALOG[key].orEmpty() else emptyList()
    }

    fun normalizeCilindraje(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotEmpty()) "${digits}cc" else raw.trim()
    }

    fun findDisplacement(marca: String, modelo: String, currentModels: List<MotoModelSpec> = emptyList()): String? {
        val normModel = modelo.trim().lowercase().replace(Regex("\\s+"), " ")
        if (normModel.isEmpty()) return null
        val fromCurrent = currentModels.firstOrNull {
            it.modelo.trim().lowercase().replace(Regex("\\s+"), " ") == normModel
        }
        if (fromCurrent != null) return normalizeCilindraje(fromCurrent.cilindraje)

        val base = baseModelsForBrand(marca).firstOrNull {
            it.modelo.trim().lowercase().replace(Regex("\\s+"), " ") == normModel
        }
        return base?.let { normalizeCilindraje(it.cilindraje) }
    }
}
