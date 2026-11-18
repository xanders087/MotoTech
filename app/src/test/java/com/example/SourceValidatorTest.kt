package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los valores son ficticios a propósito: no describen ninguna moto real. */
class SourceValidatorTest {
  private val manual = ManualInfo(titulo = "Manual de prueba", url = "https://ejemplo.com/m.pdf")

  @Test
  fun `dato con pagina es de manual oficial y enlaza al manual`() {
    val r = SourceValidator.verificarConManual("valor de prueba", 10, manual)
    assertEquals(NivelFuente.MANUAL, r.nivel)
    assertEquals("valor de prueba", r.valor)
    assertEquals("Manual de prueba, pág. 10", r.fuenteTitulo)
    assertEquals("https://ejemplo.com/m.pdf", r.fuenteUrl)
  }

  @Test
  fun `manual sin enlace conserva la referencia a la pagina`() {
    val r = SourceValidator.verificarConManual("valor de prueba", 3, ManualInfo(titulo = "Manual de prueba"))
    assertEquals(NivelFuente.MANUAL, r.nivel)
    assertEquals("Manual de prueba, pág. 3", r.fuenteTitulo)
    assertNull(r.fuenteUrl)
  }

  @Test
  fun `dato sin pagina o sin manual no se da por oficial`() {
    assertEquals(NivelFuente.SIN_FUENTE, SourceValidator.verificarConManual("valor de prueba", null, manual).nivel)
    assertEquals(NivelFuente.SIN_FUENTE, SourceValidator.verificarConManual("valor de prueba", 0, manual).nivel)
    assertEquals(NivelFuente.SIN_FUENTE, SourceValidator.verificarConManual("valor de prueba", 10, null).nivel)
  }

  @Test
  fun `valor nulo o vacio es sin dato`() {
    assertEquals(NivelFuente.SIN_DATO, SourceValidator.verificarConManual(null, 10, manual).nivel)
    assertEquals(NivelFuente.SIN_DATO, SourceValidator.verificarConManual("  ", 10, manual).nivel)
  }

  @Test
  fun `manual sin titulo o untitled usa un nombre generico`() {
    val r = SourceValidator.verificarConManual("valor de prueba", 7, ManualInfo())
    assertEquals("Manual del fabricante, pág. 7", r.fuenteTitulo)

    val rUntitled = SourceValidator.verificarConManual("valor de prueba", 8, ManualInfo(titulo = "untitled"))
    assertEquals("Manual del fabricante, pág. 8", rUntitled.fuenteTitulo)
  }

  @Test
  fun `manual aproximado devuelve nivel MANUAL_OTRO_ANIO e incluye rango`() {
    val manualOtroAnio = ManualInfo(
        titulo = "Manual de prueba",
        url = "https://ejemplo.com/m.pdf",
        anio_desde = 2020,
        anio_hasta = 2022,
        aproximada = true,
        anio_consultado = 2024
    )
    val r = SourceValidator.verificarConManual("valor de prueba", 12, manualOtroAnio)
    assertEquals(NivelFuente.MANUAL_OTRO_ANIO, r.nivel)
    assertEquals("Manual de prueba (2020-2022), pág. 12", r.fuenteTitulo)
    assertEquals("https://ejemplo.com/m.pdf", r.fuenteUrl)

    val mismoAnioRango = manualOtroAnio.copy(anio_desde = 2021, anio_hasta = 2021)
    val r2 = SourceValidator.verificarConManual("valor de prueba", 5, mismoAnioRango)
    assertEquals(NivelFuente.MANUAL_OTRO_ANIO, r2.nivel)
    assertEquals("Manual de prueba (2021), pág. 5", r2.fuenteTitulo)
  }

  @Test
  fun `dato web oficial formatea fecha y conserva unidades literales`() {
    val dato = DatoWeb(
        texto_original = "199.4 cc",
        valor_normalizado = "199 cc",
        fuente = "https://ejemplo.com/moto",
        capturado = "2026-11-16"
    )
    val r = SourceValidator.verificarConSitioOficial(dato, marca = "Bajaj")
    assertEquals(NivelFuente.SITIO_OFICIAL, r.nivel)
    assertEquals("199.4 cc", r.valor)
    assertNull(r.detalleOriginal)
    assertEquals("Sitio oficial de Bajaj · consultado el 16/11/2026", r.fuenteTitulo)
    assertEquals("https://ejemplo.com/moto", r.fuenteUrl)
  }

  @Test
  fun `dato web sin unidades muestra valor normalizado y nota del original`() {
    val dato = DatoWeb(
        texto_original = "12.0",
        valor_normalizado = "12 L",
        fuente = "https://ejemplo.com/moto",
        capturado = "2026-11-16"
    )
    val r = SourceValidator.verificarConSitioOficial(dato, marca = "Yamaha")
    assertEquals(NivelFuente.SITIO_OFICIAL, r.nivel)
    assertEquals("12 L", r.valor)
    assertEquals("En sitio oficial: 12.0", r.detalleOriginal)
  }

  @Test
  fun `dato web aproximado devuelve nivel SITIO_OFICIAL_OTRO_ANIO`() {
    val dato = DatoWeb(
        texto_original = "4 tiempos, SOHC",
        fuente = "https://ejemplo.com/moto",
        capturado = "2026-11-16"
    )
    val manualAprox = ManualInfo(
        anio_desde = 2024,
        anio_hasta = 2025,
        aproximada = true,
        anio_consultado = 2027
    )
    val r = SourceValidator.verificarConSitioOficial(dato, manual = manualAprox, marca = "Honda")
    assertEquals(NivelFuente.SITIO_OFICIAL_OTRO_ANIO, r.nivel)
    assertEquals("Sitio oficial de Honda (2024-2025) · consultado el 16/11/2026", r.fuenteTitulo)
  }

  @Test
  fun `dato web sin url https devuelve SIN_FUENTE y dato vacio devuelve SIN_DATO`() {
    val sinUrl = DatoWeb(texto_original = "150 cc", fuente = "http://inseguro.example")
    assertEquals(NivelFuente.SIN_FUENTE, SourceValidator.verificarConSitioOficial(sinUrl).nivel)
    assertEquals(NivelFuente.SIN_DATO, SourceValidator.verificarConSitioOficial(null).nivel)
    assertEquals(NivelFuente.SIN_DATO, SourceValidator.verificarConSitioOficial(DatoWeb(texto_original = " ")).nivel)
  }

  @Test
  fun `formatearFechaIso convierte YYYY-MM-DD a DD_MM_YYYY`() {
    assertEquals("16/11/2026", SourceValidator.formatearFechaIso("2026-11-16"))
    assertNull(SourceValidator.formatearFechaIso("  "))
  }
}
