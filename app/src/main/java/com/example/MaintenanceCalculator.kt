package com.example

enum class EstadoMantenimiento { VENCIDO, PROXIMO, AL_DIA, SIN_HISTORIAL }

data class UltimoServicio(
    val kilometraje: Int,
    val fechaMillis: Long,
    val notas: String = ""
)

data class ItemMantenimiento(
    val plan: PlanMantenimiento,
    val componente: String,
    val estado: EstadoMantenimiento,
    val ultimo: UltimoServicio?,
    /** Negativo si ya se pasó. Null si el plan no tiene intervalo en km o no hay historial. */
    val kmRestantes: Int?,
    /** Negativo si ya se pasó. Null si el plan no tiene intervalo en meses o no hay historial. */
    val diasRestantes: Int?
)

/**
 * Representa un mantenimiento preventivo proyectado en base al kilometraje actual de la motocicleta
 * y, si existe, al último servicio registrado por el propietario.
 */
data class ProximoMantenimientoPreventivo(
    val plan: PlanMantenimiento,
    val componente: String,
    val estado: EstadoMantenimiento,
    val ultimo: UltimoServicio?,
    /** Kilometraje exacto del odómetro en el que toca este mantenimiento. */
    val proximoKilometraje: Int?,
    /** Kilómetros restantes desde kmActual hasta proximoKilometraje (0 o negativo si ya toca/venció). */
    val kmRestantes: Int?,
    /** Kilómetros recorridos dentro del ciclo actual del intervalo. */
    val kmRecorridosEnCiclo: Int?,
    /** Fracción completada del intervalo en km (0.0f a 1.0f). */
    val progresoCiclo: Float,
    /** Días restantes si aplica intervalo por meses con historial. */
    val diasRestantes: Int?,
    /** true si se calculó desde un registro real del usuario; false si se proyectó por múltiplos del odómetro. */
    val basadoEnRegistro: Boolean
)

/**
 * Calcula qué mantenimiento toca a partir del intervalo citado y del último
 * servicio que el usuario registró. No supone desgaste: sin historial no hay veredicto.
 */
object MaintenanceCalculator {
    private const val MILLIS_DIA = 24L * 60 * 60 * 1000
    private const val DIAS_MES = 30.44
    private const val MARGEN_PROXIMO = 0.10

    /**
     * Plan preventivo estándar de referencia para visualizar próximos mantenimientos por kilometraje
     * cuando aún no se ha cargado una ficha específica de la biblioteca.
     */
    val PLAN_PREVENTIVO_REFERENCIA: List<PlanMantenimiento> = listOf(
        PlanMantenimiento(
            componente = "Lubricación y ajuste de holgura de cadena",
            intervalo_km = 1000,
            intervalo_meses = 1,
            accion = "Limpiar, lubricar y verificar tensión de la cadena de transmisión"
        ),
        PlanMantenimiento(
            componente = "Aceite de motor y filtro de aceite",
            intervalo_km = 3000,
            intervalo_meses = 6,
            accion = "Reemplazar aceite sintético/semisintético y filtro según viscosidad del motor"
        ),
        PlanMantenimiento(
            componente = "Filtro de aire",
            intervalo_km = 6000,
            intervalo_meses = 12,
            accion = "Inspeccionar, limpiar o reemplazar elemento filtrante de admisión"
        ),
        PlanMantenimiento(
            componente = "Bujía de encendido",
            intervalo_km = 8000,
            intervalo_meses = 12,
            accion = "Verificar calibración de electrodo o reemplazar bujía"
        ),
        PlanMantenimiento(
            componente = "Pastillas y sistema de frenos",
            intervalo_km = 5000,
            intervalo_meses = 6,
            accion = "Medir espesor de pastillas, discos y nivel del líquido hidráulico"
        ),
        PlanMantenimiento(
            componente = "Holgura de válvulas (admisión y escape)",
            intervalo_km = 10000,
            intervalo_meses = 12,
            accion = "Comprobar y calibrar luz de válvulas en frío con galgas"
        ),
        PlanMantenimiento(
            componente = "Líquido de frenos DOT",
            intervalo_km = 15000,
            intervalo_meses = 24,
            accion = "Purgar circuito hidráulico y reemplazar líquido de frenos"
        ),
        PlanMantenimiento(
            componente = "Kit de arrastre (piñón, corona y cadena)",
            intervalo_km = 20000,
            intervalo_meses = 24,
            accion = "Inspeccionar desgaste de dientes y elongación total de la cadena"
        )
    )

    fun claveComponente(componente: String): String =
        componente.trim().lowercase().replace(Regex("\\s+"), " ")

    fun calcular(
        plan: List<PlanMantenimiento>,
        kmActual: Int,
        ultimosServicios: Map<String, UltimoServicio>,
        ahoraMillis: Long
    ): List<ItemMantenimiento> = plan.mapNotNull { p ->
        val componente = p.componente?.trim().orEmpty()
        val intervaloKm = p.intervalo_km?.takeIf { it > 0 }
        val intervaloMeses = p.intervalo_meses?.takeIf { it > 0 }
        // Sin componente o sin ningún intervalo no hay nada que calcular.
        if (componente.isEmpty() || (intervaloKm == null && intervaloMeses == null)) return@mapNotNull null

        val ultimo = ultimosServicios[claveComponente(componente)]
            ?: return@mapNotNull ItemMantenimiento(p, componente, EstadoMantenimiento.SIN_HISTORIAL, null, null, null)

        val kmRestantes = intervaloKm?.let { ultimo.kilometraje + it - kmActual }
        val diasRestantes = intervaloMeses?.let {
            val vence = ultimo.fechaMillis + (it * DIAS_MES * MILLIS_DIA).toLong()
            Math.floorDiv(vence - ahoraMillis, MILLIS_DIA).toInt()
        }

        val vencido = (kmRestantes != null && kmRestantes <= 0) || (diasRestantes != null && diasRestantes <= 0)
        val proximo = (kmRestantes != null && kmRestantes <= intervaloKm!! * MARGEN_PROXIMO) ||
            (diasRestantes != null && diasRestantes <= intervaloMeses!! * DIAS_MES * MARGEN_PROXIMO)
        val estado = when {
            vencido -> EstadoMantenimiento.VENCIDO
            proximo -> EstadoMantenimiento.PROXIMO
            else -> EstadoMantenimiento.AL_DIA
        }
        ItemMantenimiento(p, componente, estado, ultimo, kmRestantes, diasRestantes)
    }.sortedBy { it.estado.ordinal }

    /**
     * Calcula el resumen de próximos mantenimientos preventivos en base al kilometraje actual (`kmActual`).
     * - Si el usuario registró cuándo hizo por última vez ese servicio (`ultimo != null`), calcula el próximo
     *   kilometraje sumando `ultimo.kilometraje + intervalo_km`.
     * - Si no hay registro previo para ese componente (`ultimo == null`), proyecta el próximo mantenimiento
     *   preventivo según los ciclos periódicos del odómetro (`kmActual % intervalo_km`).
     */
    fun calcularResumenPorKilometraje(
        plan: List<PlanMantenimiento>,
        kmActual: Int,
        ultimosServicios: Map<String, UltimoServicio>,
        ahoraMillis: Long
    ): List<ProximoMantenimientoPreventivo> {
        val kmSeguro = kmActual.coerceAtLeast(0)
        return plan.mapNotNull { p ->
            val componente = p.componente?.trim().orEmpty()
            val intervaloKm = p.intervalo_km?.takeIf { it > 0 }
            val intervaloMeses = p.intervalo_meses?.takeIf { it > 0 }
            if (componente.isEmpty() || (intervaloKm == null && intervaloMeses == null)) return@mapNotNull null

            val ultimo = ultimosServicios[claveComponente(componente)]

            if (ultimo != null) {
                val proximoKm = intervaloKm?.let { ultimo.kilometraje + it }
                val kmRestantes = proximoKm?.let { it - kmSeguro }
                val kmRecorridos = intervaloKm?.let { (kmSeguro - ultimo.kilometraje).coerceAtLeast(0) }
                val progreso = if (intervaloKm != null && intervaloKm > 0) {
                    ((kmSeguro - ultimo.kilometraje).toFloat() / intervaloKm.toFloat()).coerceIn(0f, 1f)
                } else 0f

                val diasRestantes = intervaloMeses?.let {
                    val vence = ultimo.fechaMillis + (it * DIAS_MES * MILLIS_DIA).toLong()
                    Math.floorDiv(vence - ahoraMillis, MILLIS_DIA).toInt()
                }

                val vencido = (kmRestantes != null && kmRestantes <= 0) || (diasRestantes != null && diasRestantes <= 0)
                val proximo = (kmRestantes != null && kmRestantes <= intervaloKm!! * MARGEN_PROXIMO) ||
                    (diasRestantes != null && diasRestantes <= intervaloMeses!! * DIAS_MES * MARGEN_PROXIMO)
                val estado = when {
                    vencido -> EstadoMantenimiento.VENCIDO
                    proximo -> EstadoMantenimiento.PROXIMO
                    else -> EstadoMantenimiento.AL_DIA
                }

                ProximoMantenimientoPreventivo(
                    plan = p,
                    componente = componente,
                    estado = estado,
                    ultimo = ultimo,
                    proximoKilometraje = proximoKm,
                    kmRestantes = kmRestantes,
                    kmRecorridosEnCiclo = kmRecorridos,
                    progresoCiclo = progreso,
                    diasRestantes = diasRestantes,
                    basadoEnRegistro = true
                )
            } else if (intervaloKm != null) {
                val residuo = kmSeguro % intervaloKm
                val tocaExactoAhora = kmSeguro > 0 && residuo == 0
                val proximoKm = when {
                    kmSeguro == 0 -> intervaloKm
                    tocaExactoAhora -> kmSeguro
                    else -> ((kmSeguro / intervaloKm) + 1) * intervaloKm
                }
                val kmRestantes = proximoKm - kmSeguro
                val kmRecorridos = if (tocaExactoAhora) intervaloKm else residuo
                val progreso = (kmRecorridos.toFloat() / intervaloKm.toFloat()).coerceIn(0f, 1f)

                val margenKm = (intervaloKm * MARGEN_PROXIMO).toInt().coerceAtLeast(150)
                val estado = when {
                    kmRestantes <= 0 -> EstadoMantenimiento.VENCIDO
                    kmRestantes <= margenKm -> EstadoMantenimiento.PROXIMO
                    else -> EstadoMantenimiento.AL_DIA
                }

                ProximoMantenimientoPreventivo(
                    plan = p,
                    componente = componente,
                    estado = estado,
                    ultimo = null,
                    proximoKilometraje = proximoKm,
                    kmRestantes = kmRestantes,
                    kmRecorridosEnCiclo = kmRecorridos,
                    progresoCiclo = progreso,
                    diasRestantes = null,
                    basadoEnRegistro = false
                )
            } else {
                ProximoMantenimientoPreventivo(
                    plan = p,
                    componente = componente,
                    estado = EstadoMantenimiento.SIN_HISTORIAL,
                    ultimo = null,
                    proximoKilometraje = null,
                    kmRestantes = null,
                    kmRecorridosEnCiclo = null,
                    progresoCiclo = 0f,
                    diasRestantes = null,
                    basadoEnRegistro = false
                )
            }
        }.sortedWith(
            compareBy<ProximoMantenimientoPreventivo> { it.estado.ordinal }
                .thenBy { it.kmRestantes ?: Int.MAX_VALUE }
        )
    }
}
