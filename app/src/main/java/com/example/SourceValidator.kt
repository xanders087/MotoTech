package com.example

enum class NivelFuente {
    /** Transcrito del manual oficial para el año consultado, con su página. */
    MANUAL,
    /** Transcrito del manual oficial, pero de otro rango de años distinto al consultado. */
    MANUAL_OTRO_ANIO,
    /** Capturado del sitio oficial del fabricante para el año consultado, con URL y fecha. */
    SITIO_OFICIAL,
    /** Capturado del sitio oficial del fabricante, pero de otro rango de años distinto al consultado. */
    SITIO_OFICIAL_OTRO_ANIO,
    /** Hay un valor, pero la ficha no indica la fuente oficial requerida. */
    SIN_FUENTE,
    /** La fuente oficial no trae ese dato. */
    SIN_DATO
}

data class DatoVerificado(
    val valor: String?,
    val nivel: NivelFuente,
    val fuenteTitulo: String?,
    val fuenteUrl: String?,
    val detalleOriginal: String? = null
)

/** Decide cómo se presenta cada dato de una ficha según la referencia oficial que trae. */
object SourceValidator {

    /** Formatea una fecha ISO AAAA-MM-DD a DD/MM/AAAA para mostrar al usuario. */
    fun formatearFechaIso(iso: String?): String? {
        val limpia = iso?.trim().orEmpty()
        if (limpia.isEmpty()) return null
        val partes = limpia.split("-")
        return if (partes.size == 3 && partes[0].length == 4 && partes[1].length == 2 && partes[2].length == 2) {
            "${partes[2]}/${partes[1]}/${partes[0]}"
        } else {
            limpia
        }
    }

    /** Formatea el rango de años del documento (ej. "2020-2022" o "2026"). */
    fun formatearRangoAnios(manual: ManualInfo?): String? {
        val desde = manual?.anio_desde ?: return null
        val hasta = manual.anio_hasta ?: return null
        return if (desde == hasta) "$desde" else "$desde-$hasta"
    }

    /** Un dato de manual vale como oficial solo si indica la página del manual. */
    fun verificarConManual(valor: String?, pagina: Int?, manual: ManualInfo?): DatoVerificado {
        val limpio = valor?.trim().orEmpty()
        if (limpio.isEmpty()) return DatoVerificado(null, NivelFuente.SIN_DATO, null, null)
        if (manual == null || pagina == null || pagina < 1) {
            return DatoVerificado(limpio, NivelFuente.SIN_FUENTE, null, null)
        }
        val titulo = manual.titulo?.takeIf { it.isNotBlank() && !it.equals("untitled", ignoreCase = true) }
            ?: "Manual del fabricante"
        val urlManual = manual.fuente_plan?.url?.takeIf { it.isNotBlank() } ?: manual.url
        if (manual.aproximada) {
            val rango = formatearRangoAnios(manual)
            val referencia = if (rango != null) "$titulo ($rango), pág. $pagina" else "$titulo, pág. $pagina"
            return DatoVerificado(limpio, NivelFuente.MANUAL_OTRO_ANIO, referencia, urlManual)
        }
        return DatoVerificado(limpio, NivelFuente.MANUAL, "$titulo, pág. $pagina", urlManual)
    }

    /**
     * Valida un dato de especificación técnica capturado del sitio oficial del fabricante.
     * Exige URL HTTPS de origen; muestra valor normalizado cuando añade unidad o conversión.
     */
    fun verificarConSitioOficial(
        dato: DatoWeb?,
        manual: ManualInfo? = null,
        marca: String? = null
    ): DatoVerificado {
        val original = dato?.texto_original?.trim().orEmpty()
        val normalizado = dato?.valor_normalizado?.trim().orEmpty()
        if (original.isEmpty() && normalizado.isEmpty()) {
            return DatoVerificado(null, NivelFuente.SIN_DATO, null, null)
        }

        val (valorPrincipal, notaOriginal) = seleccionarValorWeb(original, normalizado)
        val url = dato?.fuente?.trim().orEmpty()
        if (!url.startsWith("https://")) {
            return DatoVerificado(valorPrincipal, NivelFuente.SIN_FUENTE, null, null, notaOriginal)
        }

        val marcaLimpia = marca?.trim()?.takeIf { it.isNotEmpty() }
        val fechaFmt = formatearFechaIso(dato?.capturado ?: manual?.verificacion_fecha)
        val baseTitulo = if (marcaLimpia != null) "Sitio oficial de $marcaLimpia" else "Sitio oficial del fabricante"
        val rango = if (manual?.aproximada == true) formatearRangoAnios(manual) else null
        val referencia = buildString {
            append(baseTitulo)
            if (rango != null) append(" ($rango)")
            if (fechaFmt != null) append(" · consultado el $fechaFmt")
        }

        val nivel = if (manual?.aproximada == true) {
            NivelFuente.SITIO_OFICIAL_OTRO_ANIO
        } else {
            NivelFuente.SITIO_OFICIAL
        }
        return DatoVerificado(
            valor = valorPrincipal,
            nivel = nivel,
            fuenteTitulo = referencia,
            fuenteUrl = url,
            detalleOriginal = notaOriginal
        )
    }

    private fun seleccionarValorWeb(original: String, normalizado: String): Pair<String, String?> {
        if (original.isEmpty()) return normalizado to null
        if (normalizado.isEmpty()) return original to null

        val origCompacto = original.replace(" ", "").lowercase()
        val normCompacto = normalizado.replace(" ", "").lowercase()
        if (origCompacto == normCompacto) {
            return normalizado to null
        }

        // Si el texto original ya trae unidades (ej. "199.4 cc"), se conserva por mayor precisión literal;
        // si carece de letras/unidades (ej. "2.6" o "10.34/8.000"), se muestra el normalizado con nota original.
        return if (original.any { it.isLetter() }) {
            original to null
        } else {
            normalizado to "En sitio oficial: $original"
        }
    }
}
