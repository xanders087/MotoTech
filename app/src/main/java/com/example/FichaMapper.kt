package com.example

/**
 * Convierte los documentos de la biblioteca de fichas (colección "fichas") en lo que muestra la app.
 * El formato de esos documentos está descrito en fichas/LEEME.md.
 */
object FichaMapper {
    const val ESTADO_PUBLICADA = "publicada"

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

    /** Solo dígitos del cilindraje ("149cc" -> 149). Null si no hay número. */
    fun cilindrajeCc(cilindraje: String): Int? = cilindraje.filter { it.isDigit() }.toIntOrNull()

    private fun normalizar(texto: String): String =
        texto.trim().lowercase().replace(Regex("\\s+"), " ")

    /** De las fichas de una moto, la publicada cuyo rango de años incluye el año pedido. */
    fun seleccionar(documentos: List<Map<String, Any?>>, anio: Int): Map<String, Any?>? =
        documentos.firstOrNull { doc ->
            val desde = entero(doc["anio_desde"])
            val hasta = entero(doc["anio_hasta"])
            doc["estado"] == ESTADO_PUBLICADA && desde != null && hasta != null && anio in desde..hasta
        }

    fun toResponse(doc: Map<String, Any?>): DiagnosticResponse {
        val manual = mapa(doc["manual"])
        val revision = mapa(doc["revision"])
        val tecnica = mapa(doc["ficha_tecnica"])
        val cc = entero(doc["cilindraje_cc"])

        fun dato(campo: String): DatoTecnico? = mapa(tecnica?.get(campo))?.let {
            DatoTecnico(valor = texto(it["valor"]), pagina = entero(it["pagina"]))
        }

        return DiagnosticResponse(
            status = "success",
            moto_info = MotoInfo(
                marca = texto(doc["marca"]),
                modelo = texto(doc["modelo"]),
                cilindraje = cc?.let { "${it}cc" }
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
                anio_desde = entero(doc["anio_desde"]),
                anio_hasta = entero(doc["anio_hasta"])
            )
        )
    }

    // Firestore entrega los números como Long y los objetos como Map.
    private fun entero(valor: Any?): Int? = (valor as? Number)?.toInt()

    private fun texto(valor: Any?): String? = (valor as? String)?.trim()?.takeIf { it.isNotEmpty() }

    @Suppress("UNCHECKED_CAST")
    private fun mapa(valor: Any?): Map<String, Any?>? = valor as? Map<String, Any?>
}
