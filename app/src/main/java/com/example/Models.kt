package com.example

import com.squareup.moshi.JsonClass

/**
 * Ficha de una moto tal como la muestra la app. Todos los campos son anulables a propósito:
 * si la fuente oficial no trae un dato, queda en null y no se inventa ningún valor.
 */
@JsonClass(generateAdapter = true)
data class DiagnosticResponse(
    val status: String? = null,
    val moto_info: MotoInfo? = null,
    val ficha_tecnica: FichaTecnica? = null,
    val plan_mantenimiento_periodico: List<PlanMantenimiento>? = null,
    // Origen de los datos (sitio oficial y/o manual oficial en PDF).
    val manual: ManualInfo? = null,
    // Especificaciones técnicas v3 recolectadas de la página oficial del fabricante.
    val especificaciones: EspecificacionesTecnicas? = null
)

@JsonClass(generateAdapter = true)
data class FuentePlanPdf(
    val url: String? = null,
    val sha256: String? = null,
    val pagina: Int? = null,
    val titulo: String? = null
)

@JsonClass(generateAdapter = true)
data class ManualInfo(
    val titulo: String? = null,
    val edicion: String? = null,
    val url: String? = null,
    val revisado_por: String? = null,
    val fecha_revision: String? = null,
    val anio_desde: Int? = null,
    val anio_hasta: Int? = null,
    val aproximada: Boolean = false,
    val anio_consultado: Int? = null,
    val ficha_id: String? = null,
    val anio_origen: String? = null,
    val verificacion_metodo: String? = null,
    val verificacion_fecha: String? = null,
    val fuente_plan: FuentePlanPdf? = null
)

@JsonClass(generateAdapter = true)
data class MotoInfo(
    val marca: String? = null,
    val modelo: String? = null,
    val cilindraje: String? = null,
    val anio: Int? = null
)

/** Un dato de servicio técnico con la página del manual de la que salió. */
@JsonClass(generateAdapter = true)
data class DatoTecnico(
    val valor: String? = null,
    val pagina: Int? = null
)

/** Un dato de especificación técnica capturado del sitio oficial del fabricante. */
@JsonClass(generateAdapter = true)
data class DatoWeb(
    val texto_original: String? = null,
    val valor_normalizado: String? = null,
    val fuente: String? = null,
    val capturado: String? = null
)

/** Datos de servicio extraídos del manual del propietario (aceite, llantas, cadena, frenos). */
@JsonClass(generateAdapter = true)
data class FichaTecnica(
    val capacidad_aceite: DatoTecnico? = null,
    val viscosidad_aceite: DatoTecnico? = null,
    val presion_llanta_delantera: DatoTecnico? = null,
    val presion_llanta_trasera: DatoTecnico? = null,
    val holgura_cadena: DatoTecnico? = null,
    val liquido_frenos: DatoTecnico? = null
)

/** Especificaciones técnicas v3 agrupadas por Motor, Transmisión, Chasis y Dimensiones. */
@JsonClass(generateAdapter = true)
data class EspecificacionesTecnicas(
    // Motor
    val cilindraje: DatoWeb? = null,
    val tipo_motor: DatoWeb? = null,
    val potencia_maxima: DatoWeb? = null,
    val torque_maximo: DatoWeb? = null,
    val alimentacion: DatoWeb? = null,
    val refrigeracion: DatoWeb? = null,
    val arranque: DatoWeb? = null,
    val relacion_compresion: DatoWeb? = null,
    // Transmisión
    val tipo_transmision: DatoWeb? = null,
    val numero_velocidades: DatoWeb? = null,
    val transmision_final: DatoWeb? = null,
    // Chasis
    val freno_delantero: DatoWeb? = null,
    val freno_trasero: DatoWeb? = null,
    val abs: DatoWeb? = null,
    val suspension_delantera: DatoWeb? = null,
    val suspension_trasera: DatoWeb? = null,
    val llanta_delantera: DatoWeb? = null,
    val llanta_trasera: DatoWeb? = null,
    // Dimensiones
    val capacidad_tanque: DatoWeb? = null,
    val peso: DatoWeb? = null,
    val altura_asiento: DatoWeb? = null,
    val distancia_ejes: DatoWeb? = null
)

@JsonClass(generateAdapter = true)
data class PlanMantenimiento(
    val componente: String? = null,
    val intervalo_km: Int? = null,
    val intervalo_meses: Int? = null,
    val accion: String? = null,
    val pagina: Int? = null
)

// UI and Business Models
data class MaintenanceLogItem(
    val id: String,
    val fecha: String,
    val kilometraje: Int,
    val titulo: String,
    val taller: String,
    val costo: String,
    val facturaNumero: String,
    val categoria: String,
    val tieneComprobante: Boolean,
    val notas: String = ""
)

data class WorkshopItem(
    val id: String,
    val nombre: String,
    val direccion: String,
    val distancia: String,
    val calificacion: Double,
    val totalResenas: Int,
    val especialidades: List<String>,
    val descuentoConvenio: String,
    val telefono: String,
    val horario: String,
    val latitud: Double = 4.6097,
    val longitud: Double = -74.0817
)

data class FleetBike(
    val id: String,
    val codigo: String,
    val placa: String,
    val conductor: String,
    val modelo: String,
    val anio: Int,
    val kilometraje: Int,
    val estado: FleetHealthStatus,
    val alertaPrincipal: String,
    val diasUltimoService: Int
)

enum class FleetHealthStatus {
    OPTIMO,
    REVISION_PROXIMA,
    ATENCION_URGENTE
}
