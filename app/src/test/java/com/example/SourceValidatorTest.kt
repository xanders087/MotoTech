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
  fun `manual sin titulo usa un nombre generico`() {
    val r = SourceValidator.verificarConManual("valor de prueba", 7, ManualInfo())
    assertEquals("Manual del fabricante, pág. 7", r.fuenteTitulo)
  }
}
