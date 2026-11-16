package com.example

enum class NivelFuente {
    /** Transcrito del manual oficial, con su página. */
    MANUAL,
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
        return DatoVerificado(limpio, NivelFuente.MANUAL, "$titulo, pág. $pagina", manual.url)
    }
}
