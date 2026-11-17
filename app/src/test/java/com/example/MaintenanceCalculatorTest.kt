package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceCalculatorTest {
  private val dia = 24L * 60 * 60 * 1000
  private val ahora = 1_800_000_000_000L

  private fun plan(nombre: String, km: Int? = null, meses: Int? = null) =
      PlanMantenimiento(componente = nombre, intervalo_km = km, intervalo_meses = meses)

  private fun calcular(p: PlanMantenimiento, kmActual: Int, ultimo: UltimoServicio?) =
      MaintenanceCalculator.calcular(
          listOf(p),
          kmActual,
          if (ultimo == null) emptyMap()
          else mapOf(MaintenanceCalculator.claveComponente(p.componente!!) to ultimo),
          ahora)

  @Test
  fun `sin registro no se afirma nada`() {
    val item = calcular(plan("Aceite de motor", km = 5000), 24500, null).single()
    assertEquals(EstadoMantenimiento.SIN_HISTORIAL, item.estado)
    assertNull(item.kmRestantes)
  }

  @Test
  fun `vencido cuando se supera el intervalo en km`() {
    val item = calcular(plan("Aceite de motor", km = 5000), 24500, UltimoServicio(19000, ahora)).single()
    assertEquals(EstadoMantenimiento.VENCIDO, item.estado)
    assertEquals(-500, item.kmRestantes)
  }

  @Test
  fun `proximo cuando falta el diez por ciento o menos`() {
    val item = calcular(plan("Aceite de motor", km = 5000), 24500, UltimoServicio(20000, ahora)).single()
    assertEquals(EstadoMantenimiento.PROXIMO, item.estado)
    assertEquals(500, item.kmRestantes)
  }

  @Test
  fun `al dia cuando falta mas del margen`() {
    val item = calcular(plan("Aceite de motor", km = 5000), 24500, UltimoServicio(23000, ahora)).single()
    assertEquals(EstadoMantenimiento.AL_DIA, item.estado)
    assertEquals(3500, item.kmRestantes)
  }

  @Test
  fun `vencido por tiempo aunque falten kilometros`() {
    val haceUnAnio = ahora - 400 * dia
    val item = calcular(plan("Líquido de frenos", km = 20000, meses = 12), 24500, UltimoServicio(23000, haceUnAnio)).single()
    assertEquals(EstadoMantenimiento.VENCIDO, item.estado)
    assertTrue(item.diasRestantes!! < 0)
    assertEquals(18500, item.kmRestantes)
  }

  @Test
  fun `tareas sin intervalo citado no se calculan`() {
    val items = MaintenanceCalculator.calcular(
        listOf(plan("Bujía"), plan("Cadena", km = 0), plan("", km = 1000)), 24500, emptyMap(), ahora)
    assertTrue(items.isEmpty())
  }

  @Test
  fun `el componente coincide sin importar mayusculas ni espacios`() {
    val ultimos = mapOf(MaintenanceCalculator.claveComponente("  aceite  de MOTOR ") to UltimoServicio(23000, ahora))
    val item = MaintenanceCalculator.calcular(listOf(plan("Aceite de motor", km = 5000)), 24500, ultimos, ahora).single()
    assertEquals(EstadoMantenimiento.AL_DIA, item.estado)
  }

  @Test
  fun `los vencidos van primero`() {
    val ultimos = mapOf(
        "a" to UltimoServicio(24000, ahora),
        "b" to UltimoServicio(1000, ahora))
    val items = MaintenanceCalculator.calcular(
        listOf(plan("a", km = 5000), plan("b", km = 5000), plan("c", km = 5000)), 24500, ultimos, ahora)
    assertEquals(listOf("b", "a", "c"), items.map { it.componente })
  }

  @Test
  fun `resumen por kilometraje proyecta proximo hito cuando no hay registro previo`() {
    val items = MaintenanceCalculator.calcularResumenPorKilometraje(
        listOf(
            plan("Aceite de motor", km = 3000),
            plan("Filtro de aire", km = 6000)
        ),
        kmActual = 14800,
        ultimosServicios = emptyMap(),
        ahoraMillis = ahora
    )
    // A los 14,800 km:
    // - Aceite (cada 3,000 km) -> próximo hito 15,000 km (faltan 200 km <= 10% de 3,000 = 300 km -> PROXIMO)
    // - Filtro de aire (cada 6,000 km) -> próximo hito 18,000 km (faltan 3,200 km -> AL_DIA)
    val aceite = items.first { it.componente == "Aceite de motor" }
    val filtro = items.first { it.componente == "Filtro de aire" }

    assertEquals(15000, aceite.proximoKilometraje)
    assertEquals(200, aceite.kmRestantes)
    assertEquals(EstadoMantenimiento.PROXIMO, aceite.estado)

    assertEquals(18000, filtro.proximoKilometraje)
    assertEquals(3200, filtro.kmRestantes)
    assertEquals(EstadoMantenimiento.AL_DIA, filtro.estado)
  }

  @Test
  fun `resumen por kilometraje detecta hito exacto y respeta ultimo servicio registrado`() {
    val ultimos = mapOf(
        MaintenanceCalculator.claveComponente("Bujía") to UltimoServicio(10000, ahora)
    )
    val items = MaintenanceCalculator.calcularResumenPorKilometraje(
        listOf(
            plan("Cadena", km = 1000),
            plan("Bujía", km = 8000)
        ),
        kmActual = 15000,
        ultimosServicios = ultimos,
        ahoraMillis = ahora
    )
    val cadena = items.first { it.componente == "Cadena" }
    val bujia = items.first { it.componente == "Bujía" }

    // 15,000 km es múltiplo exacto de 1,000 km -> toca ahora (kmRestantes = 0, VENCIDO)
    assertEquals(15000, cadena.proximoKilometraje)
    assertEquals(0, cadena.kmRestantes)
    assertEquals(EstadoMantenimiento.VENCIDO, cadena.estado)

    // Bujía registrada a los 10,000 km + 8,000 km = 18,000 km (faltan 3,000 km)
    assertEquals(18000, bujia.proximoKilometraje)
    assertEquals(3000, bujia.kmRestantes)
    assertTrue(bujia.basadoEnRegistro)
  }

  @Test
  fun `evaluar alertas por kilometraje genera notificacion cuando falta menos del umbral`() {
    val preventivos = MaintenanceCalculator.calcularResumenPorKilometraje(
        listOf(
            plan("Aceite de motor", km = 3000),
            plan("Kit de arrastre", km = 20000)
        ),
        kmActual = 14600,
        ultimosServicios = emptyMap(),
        ahoraMillis = ahora
    )
    // A los 14,600 km faltan 400 km para el cambio de aceite (a los 15,000 km).
    // Con umbral de 500 km debe generarse alerta para Aceite de motor, pero no para Kit de arrastre.
    val alerta = MaintenanceNotificationHelper.evaluarAlertasPorKilometraje(
        nombreMoto = "Yamaha MT-07",
        kmActual = 14600,
        items = preventivos,
        umbralKm = 500
    )
    assertEquals(1, alerta?.tareasEnAlerta?.size)
    assertEquals("Aceite de motor", alerta?.tareasEnAlerta?.first()?.componente)
    assertTrue(alerta?.titulo?.contains("Yamaha MT-07") == true)
  }
}


