package com.example

enum class NivelFuente {
    /** Transcrito del manual oficial, con su página. */
    MANUAL,
    /** La búsqueda devolvió esa página y pertenece al fabricante. */
    OFICIAL,
    /** La búsqueda devolvió esa página, pero no es del fabricante. */
    WEB,
    /** Hay un valor, pero la fuente citada no aparece entre los resultados de la búsqueda. */
    SIN_FUENTE,
    /** No se encontró el dato. */
    SIN_DATO
}

data class DatoVerificado(
    val valor: String?,
    val nivel: NivelFuente,
    val fuenteTitulo: String?,
    val fuenteUrl: String?
)

/**
 * Contrasta lo que el modelo dice haber citado con las páginas que la búsqueda
 * realmente devolvió. Un dato cuya fuente no aparece ahí no se presenta como verificado.
 */
object SourceValidator {

    /** Dato de la biblioteca de fichas: vale como oficial solo si indica la página del manual. */
    fun verificarConManual(valor: String?, pagina: Int?, manual: ManualInfo): DatoVerificado {
        val limpio = valor?.trim().orEmpty()
        if (limpio.isEmpty()) return DatoVerificado(null, NivelFuente.SIN_DATO, null, null)
        if (pagina == null || pagina < 1) return DatoVerificado(limpio, NivelFuente.SIN_FUENTE, null, null)
        val titulo = manual.titulo?.takeIf { it.isNotBlank() } ?: "Manual del fabricante"
        return DatoVerificado(limpio, NivelFuente.MANUAL, "$titulo, pág. $pagina", manual.url)
    }

    fun verificar(dato: DatoTecnico?, fuentes: List<FuenteConsultada>, marca: String): DatoVerificado =
        verificar(dato?.valor, dato?.fuente_dominio, dato?.fuente_titulo, fuentes, marca)

    fun verificar(
        valor: String?,
        dominioCitado: String?,
        tituloCitado: String?,
        fuentes: List<FuenteConsultada>,
        marca: String
    ): DatoVerificado {
        val limpio = valor?.trim().orEmpty()
        if (limpio.isEmpty() || limpio.equals("null", ignoreCase = true)) {
            return DatoVerificado(null, NivelFuente.SIN_DATO, null, null)
        }
        val dominio = normalizarDominio(dominioCitado)
        val fuente = (if (dominio.isEmpty()) null else fuentes.firstOrNull { coincide(dominio, it) })
            ?: return DatoVerificado(limpio, NivelFuente.SIN_FUENTE, null, null)

        val nivel = if (esDominioDelFabricante(dominio, marca)) NivelFuente.OFICIAL else NivelFuente.WEB
        val titulo = tituloCitado?.trim()?.takeIf { it.isNotEmpty() } ?: fuente.titulo ?: dominio
        return DatoVerificado(limpio, nivel, titulo, fuente.url)
    }

    /** "https://www.Yamaha-Motor.com/co/x" -> "yamaha-motor.com" */
    fun normalizarDominio(texto: String?): String {
        var d = texto?.trim()?.lowercase().orEmpty()
        if (d.isEmpty()) return ""
        d = d.substringAfter("://")
        d = d.substringBefore("/").substringBefore("?").substringBefore(":")
        d = d.removePrefix("www.")
        return if (d.contains('.') && !d.contains(' ')) d else ""
    }

    private fun coincide(dominio: String, fuente: FuenteConsultada): Boolean {
        // En los resultados de búsqueda de Gemini el título suele ser el dominio
        // y la URL un enlace de redirección, por eso se revisan ambos.
        val candidatos = listOf(normalizarDominio(fuente.titulo), normalizarDominio(fuente.url))
            .filter { it.isNotEmpty() }
        return candidatos.any { it == dominio || it.endsWith(".$dominio") || dominio.endsWith(".$it") }
    }

    /** El dominio contiene el nombre de la marca: "Royal Enfield" -> royalenfield.com */
    fun esDominioDelFabricante(dominio: String, marca: String): Boolean {
        val soloLetras = { s: String -> s.lowercase().filter { it.isLetterOrDigit() } }
        val marcaNorm = soloLetras(marca)
        if (marcaNorm.length < 3) return false
        return soloLetras(dominio.substringBeforeLast('.')).contains(marcaNorm)
    }
}
