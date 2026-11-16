package com.example

/**
 * Identifica una moto sin importar mayúsculas, espacios ni el kilometraje.
 * La ficha técnica depende del modelo, no del odómetro.
 */
object VehicleKey {
    fun of(marca: String, modelo: String, cilindraje: String, anio: String): String {
        val cc = cilindraje.filter { it.isDigit() }.ifEmpty { normalize(cilindraje) }
        return listOf(normalize(marca), normalize(modelo), cc, normalize(anio)).joinToString("|")
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")
}
