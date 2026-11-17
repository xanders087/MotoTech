package com.example

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class MotoModelSpec(
    val modelo: String,
    val cilindraje: String,
    val categoria: String? = null,
    val url_fuente: String? = null,
    val tiene_ficha: Boolean = false,
    val vigente: Boolean = true
)

/**
 * Catálogo oficial de marcas y modelos. Lee en línea desde Firestore (`catalogo/{marca}`)
 * y usa `/motorcycle_catalog.json` como instantánea de respaldo sin conexión.
 * Nunca realiza scraping desde el teléfono ni infiere cilindradas.
 */
object MotorcycleCatalog {

    private val VERIFIED_CATALOG: Map<String, List<MotoModelSpec>> by lazy {
        loadCatalogFromResource()
    }

    val MARCAS: List<String> by lazy {
        VERIFIED_CATALOG.keys.toList()
    }

    fun snapshot(): Map<String, List<MotoModelSpec>> = VERIFIED_CATALOG

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

    fun parseCatalogDocuments(docs: List<Map<String, Any?>>): Map<String, List<MotoModelSpec>> {
        val resultado = linkedMapOf<String, List<MotoModelSpec>>()
        for (doc in docs) {
            val marca = (doc["marca"] as? String)?.trim().orEmpty()
            if (marca.isEmpty()) continue
            val rawModelos = doc["modelos"] as? List<*> ?: continue
            val modelos = rawModelos.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val modelo = (map["modelo"] as? String)?.trim().orEmpty()
                if (modelo.isEmpty()) return@mapNotNull null

                val ccNum = (map["cilindraje_cc"] as? Number)?.toInt()
                val cilindraje = when {
                    ccNum != null && ccNum > 0 -> "${ccNum}cc"
                    map["cilindraje"] is String -> normalizeCilindraje(map["cilindraje"] as String)
                    else -> ""
                }
                if (cilindraje.isEmpty() || cilindraje.none { it.isDigit() }) return@mapNotNull null

                val categoria = (map["categoria"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                val urlFuente = (map["url_fuente"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                val tieneFicha = map["tiene_ficha"] as? Boolean ?: false
                val vigente = map["vigente"] as? Boolean ?: true

                MotoModelSpec(
                    modelo = modelo,
                    cilindraje = cilindraje,
                    categoria = categoria,
                    url_fuente = urlFuente,
                    tiene_ficha = tieneFicha,
                    vigente = vigente
                )
            }
            if (modelos.isNotEmpty()) {
                resultado[marca] = modelos
            }
        }
        return resultado
    }

    fun mergeWithOfflineCatalog(
        remote: Map<String, List<MotoModelSpec>>,
        offline: Map<String, List<MotoModelSpec>> = VERIFIED_CATALOG
    ): Map<String, List<MotoModelSpec>> {
        if (remote.isEmpty()) return offline
        val merged = linkedMapOf<String, List<MotoModelSpec>>()
        merged.putAll(offline)
        for ((marcaRemota, modelosRemotos) in remote) {
            if (modelosRemotos.isEmpty()) continue
            val claveExistente = merged.keys.firstOrNull { it.equals(marcaRemota.trim(), ignoreCase = true) }
            if (claveExistente != null) {
                merged[claveExistente] = modelosRemotos
            } else {
                merged[marcaRemota.trim()] = modelosRemotos
            }
        }
        return merged
    }

    fun modelsForBrand(
        marca: String,
        catalog: Map<String, List<MotoModelSpec>> = VERIFIED_CATALOG
    ): List<MotoModelSpec> {
        val clean = marca.trim()
        val key = catalog.keys.firstOrNull { it.equals(clean, ignoreCase = true) }
        return if (key != null) catalog[key].orEmpty() else emptyList()
    }

    fun baseModelsForBrand(marca: String): List<MotoModelSpec> =
        modelsForBrand(marca, VERIFIED_CATALOG)

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
