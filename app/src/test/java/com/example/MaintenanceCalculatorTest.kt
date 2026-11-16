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
}
