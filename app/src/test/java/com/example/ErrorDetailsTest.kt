package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorDetailsTest {
  @Test
  fun `limite cero o plan gratuito es cuota cero`() {
    assertTrue(ErrorDetails.esCuotaCero(
        "Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0, model: x"))
    assertTrue(ErrorDetails.esCuotaCero("You exceeded your current quota, please check your plan and billing details."))
    assertTrue(ErrorDetails.esCuotaCero("Quota exceeded, limit: 0"))
  }

  @Test
  fun `limite temporal no es cuota cero`() {
    assertFalse(ErrorDetails.esCuotaCero("Quota exceeded for quota metric 'Requests per minute', limit: 10"))
    assertFalse(ErrorDetails.esCuotaCero("Resource has been exhausted (e.g. check quota)."))
    assertFalse(ErrorDetails.esCuotaCero("Quota exceeded, limit: 0.5"))
    assertFalse(ErrorDetails.esCuotaCero(null))
  }

  @Test
  fun `detalle incluye el tipo y recorta mensajes largos`() {
    assertEquals("IllegalStateException: fallo", ErrorDetails.detalle(IllegalStateException("fallo")))
    assertNull(ErrorDetails.detalle(IllegalStateException()))
    val largo = ErrorDetails.detalle(RuntimeException("x".repeat(2000)))!!
    assertTrue(largo.length < 750 && largo.endsWith("…"))
  }
}
