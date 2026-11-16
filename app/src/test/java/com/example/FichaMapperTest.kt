package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los valores son ficticios a propósito: no describen ninguna moto real. */
class FichaMapperTest {
  // Firestore entrega los números como Long.
  private fun doc(desde: Long = 2020, hasta: Long = 2022, estado: String = "publicada"): Map<String, Any?> = mapOf(
      "marca" to "Marca Prueba",
      "modelo" to "Modelo X 1",
      "cilindraje_cc" to 123L,
      "anio_desde" to desde,
      "anio_hasta" to hasta,
      "estado" to estado,
      "clave" to "marca prueba|modelo x 1|123",
      "manual" to mapOf("titulo" to "Manual de prueba", "edicion" to "1a", "url" to null),
      "revision" to mapOf("revisado_por" to "Persona de prueba", "fecha" to "2026-11-01"),
      "ficha_tecnica" to mapOf(
          "capacidad_aceite" to mapOf("valor" to "valor de prueba", "pagina" to 10L),
          "holgura_cadena" to null),
      "plan_mantenimiento" to listOf(
          mapOf("componente" to "Tarea A", "accion" to "Revisar", "intervalo_km" to 1000L,
              "intervalo_meses" to null, "pagina" to 20L)))

  @Test
  fun `la clave coincide con la del validador de fichas`() {
    // Mismo caso que test_clave_coincide_con_la_de_la_app en herramientas/fichas/test_validar.py
    assertEquals("marca prueba|modelo x 1|123", FichaMapper.clave("  Marca   Prueba ", "MODELO X 1", "123cc"))
    assertEquals("marca prueba|modelo x 1|123", FichaMapper.clave("Marca Prueba", "Modelo X 1", "123 CC"))
  }

  @Test
  fun `el id de solicitud es unico por moto y anio y valido para Firestore`() {
    val id = FichaMapper.idSolicitud("  Marca   Prueba ", "MODELO X/1", "123cc", 2021)
    assertEquals("marca-prueba-modelo-x-1-123-2021", id)
    assertEquals(id, FichaMapper.idSolicitud("marca prueba", "modelo x/1", "123 CC", 2021))
    assertEquals("marca-prueba-modelo-x-1-123-2022", FichaMapper.idSolicitud("Marca Prueba", "Modelo X/1", "123cc", 2022))
  }

  @Test
  fun `extrae el cilindraje en cc`() {
    assertEquals(123, FichaMapper.cilindrajeCc("123cc"))
    assertEquals(123, FichaMapper.cilindrajeCc(" 123 CC "))
    assertNull(FichaMapper.cilindrajeCc("sin dato"))
  }

  @Test
  fun `elige la ficha cuyo rango incluye el anio`() {
    val docs = listOf(doc(2020, 2022), doc(2023, 2024))
    assertEquals(2020, (FichaMapper.seleccionar(docs, 2022)!!["anio_desde"] as Number).toInt())
    assertEquals(2023, (FichaMapper.seleccionar(docs, 2023)!!["anio_desde"] as Number).toInt())
    assertNull(FichaMapper.seleccionar(docs, 2019))
    assertNull(FichaMapper.seleccionar(emptyList(), 2021))
  }

  @Test
  fun `no elige fichas sin publicar`() {
    assertNull(FichaMapper.seleccionar(listOf(doc(estado = "pendiente")), 2021))
  }

  @Test
  fun `convierte la ficha conservando la pagina de cada dato`() {
    val r = FichaMapper.toResponse(doc())
    assertEquals("valor de prueba", r.ficha_tecnica?.capacidad_aceite?.valor)
    assertEquals(10, r.ficha_tecnica?.capacidad_aceite?.pagina)
    assertNull(r.ficha_tecnica?.holgura_cadena)
    assertNull(r.ficha_tecnica?.liquido_frenos)
    val tarea = r.plan_mantenimiento_periodico!!.single()
    assertEquals("Tarea A", tarea.componente)
    assertEquals(1000, tarea.intervalo_km)
    assertNull(tarea.intervalo_meses)
    assertEquals(20, tarea.pagina)
    assertEquals("Manual de prueba", r.manual?.titulo)
    assertNull(r.manual?.url)
    assertEquals("2026-11-01", r.manual?.fecha_revision)
    assertEquals("123cc", r.moto_info?.cilindraje)
  }

  @Test
  fun `la ficha de manual se puede guardar y volver a leer`() {
    val adapter = com.squareup.moshi.Moshi.Builder().build().adapter(DiagnosticResponse::class.java)
    val original = FichaMapper.toResponse(doc())
    assertEquals(original, adapter.fromJson(adapter.toJson(original)))
  }
}
