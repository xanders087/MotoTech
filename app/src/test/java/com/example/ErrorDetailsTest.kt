package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorDetailsTest {
  @Test
  fun `detalle incluye el tipo y recorta mensajes largos`() {
    assertEquals("IllegalStateException: fallo", ErrorDetails.detalle(IllegalStateException("fallo")))
    assertNull(ErrorDetails.detalle(IllegalStateException()))
    val largo = ErrorDetails.detalle(RuntimeException("x".repeat(2000)))!!
    assertTrue(largo.length < 750 && largo.endsWith("…"))
  }
}
