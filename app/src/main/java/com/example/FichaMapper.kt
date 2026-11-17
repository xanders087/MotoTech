package com.example

import java.text.Normalizer

/**
 * Convierte los documentos de la biblioteca de fichas (colección "fichas") en lo que muestra la app.
 * El formato de esos documentos está descrito en fichas/LEEME.md.
 */
object FichaMapper {
    const val ESTADO_PUBLICADA = "publicada"

    data class SeleccionFicha(
        val documento: Map<String, Any?>,
        val exacta: Boolean
    )

    /** Clave de búsqueda marca|modelo|cilindraje. Debe coincidir con clave() de herramientas/fichas/validar.py. */
    fun clave(marca: String, modelo: String, cilindraje: String): String {
        val cc = cilindraje.filter { it.isDigit() }
        return "${normalizar(marca)}|${normalizar(modelo)}|$cc"
    }

    /**
     * Id del documento de una solicitud de ficha: una por moto y año, para sumar las repetidas.
     * Solo letras, números y guiones, porque un id de Firestore no admite "/".
     */
    fun idSolicitud(marca: String, modelo: String, cilindraje: String, anio: Int): String =
        "${clave(marca, modelo, cilindraje)}|$anio"
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(200)

    /**
     * Id determinista para reportes de un campo específico de una ficha:
     * todos los reportes sobre ese campo de esa ficha suman en el mismo documento.
     */
    fun idReporte(fichaId: String, campo: String): String {
        val limpioFicha = slug(fichaId).take(110)
        val limpioCampo = slug(campo).take(85)
        return "${limpioFicha}__${limpioCampo}".take(200)
    }

    /** Identificador de respaldo cuando el documento no trae su id de Firestore. */
    fun idFichaFallback(
        marca: String,
        modelo: String,
        cilindraje: String,
        anioDesde: Int?,
        anioHasta: Int?
    ): String {
        val cc = cilindrajeCc(cilindraje) ?: 0
        val desde = anioDesde ?: 0
        val hasta = anioHasta ?: desde
        return "${slug(marca)}_${slug(modelo)}_${cc}_${desde}-${hasta}"
    }

    /** Solo dígitos del cilindraje ("149cc" -> 149). Null si no hay número. */
    fun cilindrajeCc(cilindraje: String): Int? = cilindraje.filter { it.isDigit() }.toIntOrNull()

    private fun normalizar(texto: String): String =
        texto.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun slug(texto: String): String {
        val sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return sinTildes.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    /**
     * De las fichas publicadas de una moto, devuelve primero la cuyo rango incluye el año pedido;
     * si ninguna lo incluye, devuelve la del rango más cercano (en empate, la más reciente).
     */
    fun seleccionarConCercania(documentos: List<Map<String, Any?>>, anio: Int): SeleccionFicha? {
        if (anio <= 0) return null
        val candidatas = documentos.mapNotNull { doc ->
            val desde = entero(doc["anio_desde"])
            val hasta = entero(doc["anio_hasta"])
            if (doc["estado"] != ESTADO_PUBLICADA || desde == null || hasta == null || desde > hasta) {
                null
            } else {
                val distancia = when {
                    anio in desde..hasta -> 0
                    anio < desde -> desde - anio
                    else -> anio - hasta
                }
                Triple(doc, distancia, hasta to desde)
            }
        }
        val elegida = candidatas.minWithOrNull(
            compareBy<Triple<Map<String, Any?>, Int, Pair<Int, Int>>> { it.second }
                .thenByDescending { it.third.first }
                .thenByDescending { it.third.second }
        ) ?: return null
        return SeleccionFicha(
            documento = elegida.first,
            exacta = elegida.second == 0
        )
    }

    /** Compatibilidad: devuelve el documento seleccionado para el año exacto o el más cercano. */
    fun seleccionar(documentos: List<Map<String, Any?>>, anio: Int): Map<String, Any?>? =
        seleccionarConCercania(documentos, anio)?.documento

    fun toResponse(
        doc: Map<String, Any?>,
        aproximada: Boolean = false,
        anioConsultado: Int? = null
    ): DiagnosticResponse {
        val manual = mapa(doc["manual"])
        val revision = mapa(doc["revision"])
        val tecnica = mapa(doc["ficha_tecnica"])
        val cc = entero(doc["cilindraje_cc"])
        val marca = texto(doc["marca"])
        val modelo = texto(doc["modelo"])
        val anioDesde = entero(doc["anio_desde"])
        val anioHasta = entero(doc["anio_hasta"])
        val fichaId = texto(doc["_docId"]) ?: if (marca != null && modelo != null && cc != null) {
            idFichaFallback(marca, modelo, "${cc}cc", anioDesde, anioHasta)
        } else null

        fun dato(campo: String): DatoTecnico? = mapa(tecnica?.get(campo))?.let {
            DatoTecnico(valor = texto(it["valor"]), pagina = entero(it["pagina"]))
        }

        return DiagnosticResponse(
            status = "success",
            moto_info = MotoInfo(
                marca = marca,
                modelo = modelo,
                cilindraje = cc?.let { "${it}cc" },
                anio = anioConsultado
            ),
            ficha_tecnica = FichaTecnica(
                capacidad_aceite = dato("capacidad_aceite"),
                viscosidad_aceite = dato("viscosidad_aceite"),
                presion_llanta_delantera = dato("presion_llanta_delantera"),
                presion_llanta_trasera = dato("presion_llanta_trasera"),
                holgura_cadena = dato("holgura_cadena"),
                liquido_frenos = dato("liquido_frenos")
            ),
            plan_mantenimiento_periodico = (doc["plan_mantenimiento"] as? List<*>).orEmpty()
                .mapNotNull { mapa(it) }
                .map {
                    PlanMantenimiento(
                        componente = texto(it["componente"]),
                        intervalo_km = entero(it["intervalo_km"]),
                        intervalo_meses = entero(it["intervalo_meses"]),
                        accion = texto(it["accion"]),
                        pagina = entero(it["pagina"])
                    )
                },
            manual = ManualInfo(
                titulo = texto(manual?.get("titulo")),
                edicion = texto(manual?.get("edicion")),
                url = texto(manual?.get("url")),
                revisado_por = texto(revision?.get("revisado_por")),
                fecha_revision = texto(revision?.get("fecha")),
                anio_desde = anioDesde,
                anio_hasta = anioHasta,
                aproximada = aproximada,
                anio_consultado = anioConsultado,
                ficha_id = fichaId
            )
        )
    }

    // Firestore entrega los números como Long y los objetos como Map.
    private fun entero(valor: Any?): Int? = (valor as? Number)?.toInt()

    private fun texto(valor: Any?): String? = (valor as? String)?.trim()?.takeIf { it.isNotEmpty() }

    @Suppress("UNCHECKED_CAST")
    private fun mapa(valor: Any?): Map<String, Any?>? = valor as? Map<String, Any?>
}
