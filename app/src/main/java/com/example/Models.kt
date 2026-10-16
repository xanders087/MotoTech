package com.example

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DiagnosticResponse(
    val status: String,
    val moto_info: MotoInfo? = null,
    val ficha_tecnica: FichaTecnica? = null,
    val diagnostico_kilometraje: DiagnosticoKilometraje? = null,
    val plan_mantenimiento_periodico: List<PlanMantenimiento>? = null,
    val recomendaciones_clave: List<String>? = null,
    val mensaje: String? = null,
    val campos_faltantes: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class MotoInfo(
    val marca: String,
    val modelo: String,
    val cilindraje: String,
    val anio: Int,
    val kilometraje_actual: Int
)

@JsonClass(generateAdapter = true)
data class FichaTecnica(
    val capacidad_aceite_litros: String,
    val viscosidad_aceite_recomendada: String,
    val presion_llantas_psi: PresionLlantas,
    val holgura_cadena_mm: String,
    val tipo_liquido_frenos: String
)

@JsonClass(generateAdapter = true)
data class PresionLlantas(
    val delantera_solo: Int,
    val delantera_acompanante: Int,
    val trasera_solo: Int,
    val trasera_acompanante: Int
)

@JsonClass(generateAdapter = true)
data class DiagnosticoKilometraje(
    val atencion_inmediata: List<AtencionInmediata> = emptyList(),
    val proxima_revision: List<ProximaRevision> = emptyList(),
    val rutina_habitual: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AtencionInmediata(
    val componente: String,
    val motivo: String,
    val prioridad: String
)

@JsonClass(generateAdapter = true)
data class ProximaRevision(
    val componente: String,
    val kilometros_restantes: Int,
    val prioridad: String
)

@JsonClass(generateAdapter = true)
data class PlanMantenimiento(
    val componente: String,
    val intervalo_km: Int,
    val accion: String
)

// UI and Business Models
data class RepuestoAfiliado(
    val nombre: String,
    val categoria: String,
    val marcaRecomendada: String,
    val descuentoPct: String,
    val precioEstimado: String,
    val cupon: String,
    val tiendaAliada: String
)

data class MaintenanceLogItem(
    val id: String,
    val fecha: String,
    val kilometraje: Int,
    val titulo: String,
    val taller: String,
    val costo: String,
    val facturaNumero: String,
    val categoria: String,
    val tieneComprobante: Boolean
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
