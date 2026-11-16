package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceValidatorTest {
  // Así llegan los resultados de búsqueda: el título es el dominio y la URL es una redirección.
  private val fuentes = listOf(
      FuenteConsultada("yamaha-motor.com", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/abc"),
      FuenteConsultada("motoforo.example.com", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/def"))

  private fun dato(valor: String?, dominio: String?) =
      DatoTecnico(valor = valor, fuente_titulo = "Manual", fuente_dominio = dominio, tipo_fuente = "manual_fabricante")

  @Test
  fun `dominio del fabricante devuelto por la busqueda es fuente oficial`() {
    val r = SourceValidator.verificar(dato("3.0 L", "www.yamaha-motor.com"), fuentes, "Yamaha")
    assertEquals(NivelFuente.OFICIAL, r.nivel)
    assertEquals("3.0 L", r.valor)
    assertEquals(fuentes[0].url, r.fuenteUrl)
  }

  @Test
  fun `otro sitio devuelto por la busqueda es fuente web aunque el modelo diga manual`() {
    val r = SourceValidator.verificar(dato("3.0 L", "motoforo.example.com"), fuentes, "Yamaha")
    assertEquals(NivelFuente.WEB, r.nivel)
  }

  @Test
  fun `fuente citada que la busqueda no devolvio no se da por verificada`() {
    val r = SourceValidator.verificar(dato("3.0 L", "honda.com"), fuentes, "Honda")
    assertEquals(NivelFuente.SIN_FUENTE, r.nivel)
    assertNull(r.fuenteUrl)
  }

  @Test
  fun `valor sin dominio citado no se da por verificado`() {
    assertEquals(NivelFuente.SIN_FUENTE, SourceValidator.verificar(dato("3.0 L", null), fuentes, "Yamaha").nivel)
  }

  @Test
  fun `valor nulo o vacio es sin dato`() {
    assertEquals(NivelFuente.SIN_DATO, SourceValidator.verificar(dato(null, "yamaha-motor.com"), fuentes, "Yamaha").nivel)
    assertEquals(NivelFuente.SIN_DATO, SourceValidator.verificar(dato("  ", "yamaha-motor.com"), fuentes, "Yamaha").nivel)
    assertEquals(NivelFuente.SIN_DATO, SourceValidator.verificar(null, fuentes, "Yamaha").nivel)
  }

  @Test
  fun `marca con espacios coincide con su dominio`() {
    val f = listOf(FuenteConsultada("royalenfield.com", "https://example.com/r"))
    assertEquals(NivelFuente.OFICIAL, SourceValidator.verificar(dato("15W-50", "royalenfield.com"), f, "Royal Enfield").nivel)
  }

  @Test
  fun `normaliza dominios`() {
    assertEquals("yamaha-motor.com", SourceValidator.normalizarDominio("https://www.Yamaha-Motor.com/co/manual.pdf"))
    assertEquals("", SourceValidator.normalizarDominio("Manual del propietario"))
    assertEquals("", SourceValidator.normalizarDominio(null))
  }
}
