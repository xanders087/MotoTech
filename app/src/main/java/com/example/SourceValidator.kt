package com.example

enum class NivelFuente {
    /** Transcrito del manual oficial para el año consultado, con su página. */
    MANUAL,
    /** Transcrito del manual oficial, pero de otro rango de años distinto al consultado. */
    MANUAL_OTRO_ANIO,
    /** Hay un valor, pero la ficha no indica en qué página del manual está. */
    SIN_FUENTE,
    /** El manual no trae ese dato. */
    SIN_DATO
}

data class DatoVerificado(
    val valor: String?,
    val nivel: NivelFuente,
    val fuenteTitulo: String?,
    val fuenteUrl: String?
)

/** Decide cómo se presenta cada dato de una ficha según la referencia al manual que trae. */
object SourceValidator {

    /** Un dato vale como oficial solo si indica la página del manual. */
    fun verificarConManual(valor: String?, pagina: Int?, manual: ManualInfo?): DatoVerificado {
        val limpio = valor?.trim().orEmpty()
        if (limpio.isEmpty()) return DatoVerificado(null, NivelFuente.SIN_DATO, null, null)
        if (manual == null || pagina == null || pagina < 1) {
            return DatoVerificado(limpio, NivelFuente.SIN_FUENTE, null, null)
        }
        val titulo = manual.titulo?.takeIf { it.isNotBlank() } ?: "Manual del fabricante"
        if (manual.aproximada) {
            val rango = when {
                manual.anio_desde != null && manual.anio_hasta != null ->
                    if (manual.anio_desde == manual.anio_hasta) "${manual.anio_desde}"
                    else "${manual.anio_desde}-${manual.anio_hasta}"
                else -> null
            }
            val referencia = if (rango != null) "$titulo ($rango), pág. $pagina" else "$titulo, pág. $pagina"
            return DatoVerificado(limpio, NivelFuente.MANUAL_OTRO_ANIO, referencia, manual.url)
        }
        return DatoVerificado(limpio, NivelFuente.MANUAL, "$titulo, pág. $pagina", manual.url)
    }
}
