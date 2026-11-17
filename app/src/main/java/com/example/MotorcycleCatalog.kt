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

@JsonClass(generateAdapter = true)
data class BrandCatalogResponse(
    val marca: String? = null,
    val modelos: List<MotoModelSpec>? = null
)

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

    fun mergeModels(base: List<MotoModelSpec>, dynamic: List<MotoModelSpec>): List<MotoModelSpec> {
        val map = LinkedHashMap<String, MotoModelSpec>()
        for (item in base) {
            val key = item.modelo.trim().lowercase().replace(Regex("\\s+"), " ")
            if (key.isNotEmpty() && item.cilindraje.isNotBlank()) {
                map[key] = item.copy(cilindraje = normalizeCilindraje(item.cilindraje))
            }
        }
        for (item in dynamic) {
            val cleanModel = item.modelo.trim()
            val cleanCc = normalizeCilindraje(item.cilindraje)
            val key = cleanModel.lowercase().replace(Regex("\\s+"), " ")
            if (key.isNotEmpty() && cleanCc.any { it.isDigit() } && !map.containsKey(key)) {
                map[key] = MotoModelSpec(
                    modelo = cleanModel,
                    cilindraje = cleanCc,
                    categoria = item.categoria?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
        }
        return map.values.toList()
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

    fun parseBrandCatalogJson(
        text: String,
        adapter: JsonAdapter<BrandCatalogResponse>
    ): List<MotoModelSpec> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val parsed = runCatching { adapter.fromJson(text.substring(start, end + 1)) }.getOrNull()
        return parsed?.modelos.orEmpty().mapNotNull { spec ->
            val mod = spec.modelo.trim()
            val cc = normalizeCilindraje(spec.cilindraje)
            if (mod.isEmpty() || !cc.any { it.isDigit() }) null
            else MotoModelSpec(mod, cc, spec.categoria?.trim()?.takeIf { it.isNotEmpty() })
        }
    }
}
