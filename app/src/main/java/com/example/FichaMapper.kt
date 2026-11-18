package com.example

import java.text.Normalizer

/**
 * Convierte los documentos de la biblioteca de fichas (colección "fichas") en lo que muestra la app.
 * Soporta tanto las fichas v3 generadas por el recolector oficial (especificaciones web +
 * datos_servicio + plan_mantenimiento con fuente PDF/SHA-256) como las fichas manuales previas.
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
        val verificacion = mapa(doc["verificacion"])
        val tecnica = mapa(doc["ficha_tecnica"])
        val datosServicioMap = mapa(doc["datos_servicio"])
        val cc = entero(doc["cilindraje_cc"])
        val marca = texto(doc["marca"])
        val modelo = texto(doc["modelo"])
        val anioDesde = entero(doc["anio_desde"])
        val anioHasta = entero(doc["anio_hasta"])
        val anioOrigen = texto(doc["anio_origen"])
        val urlManual = texto(doc["url_manual"])
        val fichaId = texto(doc["_docId"]) ?: if (marca != null && modelo != null && cc != null) {
            idFichaFallback(marca, modelo, "${cc}cc", anioDesde, anioHasta)
        } else null

        val servicioSource = datosServicioMap ?: tecnica
        fun datoServicio(campo: String): DatoTecnico? = mapa(servicioSource?.get(campo))?.let {
            val valor = texto(it["valor"])
            val pagina = entero(it["pagina"])
            if (valor != null || pagina != null) DatoTecnico(valor = valor, pagina = pagina) else null
        }

        fun datoWeb(campo: String): DatoWeb? = mapa(tecnica?.get(campo))?.let {
            val original = texto(it["texto_original"])
            val normalizado = texto(it["valor_normalizado"])
            val fuente = texto(it["fuente"])
            val capturado = texto(it["capturado"])
            if (original != null || normalizado != null || fuente != null) {
                DatoWeb(
                    texto_original = original,
                    valor_normalizado = normalizado,
                    fuente = fuente,
                    capturado = capturado
                )
            } else null
        }

        val especificaciones = EspecificacionesTecnicas(
            cilindraje = datoWeb("cilindraje"),
            tipo_motor = datoWeb("tipo_motor"),
            potencia_maxima = datoWeb("potencia_maxima"),
            torque_maximo = datoWeb("torque_maximo"),
            alimentacion = datoWeb("alimentacion"),
            refrigeracion = datoWeb("refrigeracion"),
            arranque = datoWeb("arranque"),
            relacion_compresion = datoWeb("relacion_compresion"),
            tipo_transmision = datoWeb("tipo_transmision"),
            numero_velocidades = datoWeb("numero_velocidades"),
            transmision_final = datoWeb("transmision_final"),
            freno_delantero = datoWeb("freno_delantero"),
            freno_trasero = datoWeb("freno_trasero"),
            abs = datoWeb("abs"),
            suspension_delantera = datoWeb("suspension_delantera"),
            suspension_trasera = datoWeb("suspension_trasera"),
            llanta_delantera = datoWeb("llanta_delantera"),
            llanta_trasera = datoWeb("llanta_trasera"),
            capacidad_tanque = datoWeb("capacidad_tanque"),
            peso = datoWeb("peso"),
            altura_asiento = datoWeb("altura_asiento"),
            distancia_ejes = datoWeb("distancia_ejes")
        ).takeIf { it.tieneAlgunDato() }

        val servicio = FichaTecnica(
            capacidad_aceite = datoServicio("capacidad_aceite"),
            viscosidad_aceite = datoServicio("viscosidad_aceite"),
            presion_llanta_delantera = datoServicio("presion_llanta_delantera"),
            presion_llanta_trasera = datoServicio("presion_llanta_trasera"),
            holgura_cadena = datoServicio("holgura_cadena"),
            liquido_frenos = datoServicio("liquido_frenos")
        ).takeIf { it.tieneAlgunDato() }

        var fuentePlanPdf: FuentePlanPdf? = null
        val planRaw = doc["plan_mantenimiento"]
        val tareasPlan: List<PlanMantenimiento> = when (planRaw) {
            is List<*> -> planRaw.mapNotNull { mapa(it) }.map {
                PlanMantenimiento(
                    componente = texto(it["componente"]),
                    intervalo_km = entero(it["intervalo_km"]),
                    intervalo_meses = entero(it["intervalo_meses"]),
                    accion = texto(it["accion"]),
                    pagina = entero(it["pagina"])
                )
            }
            is Map<*, *> -> {
                val planObj = mapa(planRaw)
                val fuenteMap = mapa(planObj?.get("fuente"))
                if (fuenteMap != null) {
                    fuentePlanPdf = FuentePlanPdf(
                        url = texto(fuenteMap["url"]),
                        sha256 = texto(fuenteMap["sha256"]),
                        pagina = entero(fuenteMap["pagina"]),
                        titulo = texto(fuenteMap["titulo"])
                    )
                }
                val paginaComun = fuentePlanPdf?.pagina
                (planObj?.get("tareas") as? List<*>).orEmpty()
                    .mapNotNull { mapa(it) }
                    .map {
                        PlanMantenimiento(
                            componente = texto(it["componente"]),
                            intervalo_km = entero(it["intervalo_km"]),
                            intervalo_meses = entero(it["intervalo_meses"]),
                            accion = texto(it["accion"]),
                            pagina = entero(it["pagina"]) ?: paginaComun
                        )
                    }
            }
            else -> emptyList()
        }

        val verificacionMetodo = texto(verificacion?.get("metodo"))
        val verificacionFecha = texto(verificacion?.get("fecha"))
        val tituloManual = texto(manual?.get("titulo"))
            ?: fuentePlanPdf?.titulo?.takeIf { !it.equals("untitled", ignoreCase = true) }
        val urlOficialManual = texto(manual?.get("url")) ?: urlManual ?: fuentePlanPdf?.url

        return DiagnosticResponse(
            status = "success",
            moto_info = MotoInfo(
                marca = marca,
                modelo = modelo,
                cilindraje = cc?.let { "${it}cc" },
                anio = anioConsultado
            ),
            ficha_tecnica = servicio,
            plan_mantenimiento_periodico = tareasPlan,
            manual = ManualInfo(
                titulo = tituloManual,
                edicion = texto(manual?.get("edicion")),
                url = urlOficialManual,
                revisado_por = texto(revision?.get("revisado_por")),
                fecha_revision = texto(revision?.get("fecha")) ?: verificacionFecha,
                anio_desde = anioDesde,
                anio_hasta = anioHasta,
                aproximada = aproximada,
                anio_consultado = anioConsultado,
                ficha_id = fichaId,
                anio_origen = anioOrigen,
                verificacion_metodo = verificacionMetodo,
                verificacion_fecha = verificacionFecha,
                fuente_plan = fuentePlanPdf
            ),
            especificaciones = especificaciones
        )
    }

    private fun FichaTecnica.tieneAlgunDato(): Boolean = listOf(
        capacidad_aceite,
        viscosidad_aceite,
        presion_llanta_delantera,
        presion_llanta_trasera,
        holgura_cadena,
        liquido_frenos
    ).any { it != null }

    private fun EspecificacionesTecnicas.tieneAlgunDato(): Boolean = listOf(
        cilindraje, tipo_motor, potencia_maxima, torque_maximo, alimentacion,
        refrigeracion, arranque, relacion_compresion, tipo_transmision,
        numero_velocidades, transmision_final, freno_delantero, freno_trasero,
        abs, suspension_delantera, suspension_trasera, llanta_delantera,
        llanta_trasera, capacidad_tanque, peso, altura_asiento, distancia_ejes
    ).any { it != null }

    // Firestore entrega los números como Long y los objetos como Map.
    private fun entero(valor: Any?): Int? = (valor as? Number)?.toInt()

    private fun texto(valor: Any?): String? = (valor as? String)?.trim()?.takeIf { it.isNotEmpty() }

    @Suppress("UNCHECKED_CAST")
    private fun mapa(valor: Any?): Map<String, Any?>? = valor as? Map<String, Any?>
}
