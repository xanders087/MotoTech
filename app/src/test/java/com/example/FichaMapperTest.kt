package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los valores son ficticios a propósito: no describen ninguna moto real. */
class FichaMapperTest {
  // Firestore entrega los números como Long.
  private fun doc(
      desde: Long = 2020,
      hasta: Long = 2022,
      estado: String = "publicada",
      docId: String? = null
  ): Map<String, Any?> = buildMap {
    put("marca", "Marca Prueba")
    put("modelo", "Modelo X 1")
    put("cilindraje_cc", 123L)
    put("anio_desde", desde)
    put("anio_hasta", hasta)
    put("estado", estado)
    put("clave", "marca prueba|modelo x 1|123")
    put("manual", mapOf("titulo" to "Manual de prueba", "edicion" to "1a", "url" to null))
    put("revision", mapOf("revisado_por" to "Persona de prueba", "fecha" to "2026-11-01"))
    put("ficha_tecnica", mapOf(
        "capacidad_aceite" to mapOf("valor" to "valor de prueba", "pagina" to 10L),
        "holgura_cadena" to null))
    put("plan_mantenimiento", listOf(
        mapOf("componente" to "Tarea A", "accion" to "Revisar", "intervalo_km" to 1000L,
            "intervalo_meses" to null, "pagina" to 20L)))
    if (docId != null) put("_docId", docId)
  }

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
  fun `el id de reporte es determinista por ficha y campo y valido para Firestore`() {
    val id1 = FichaMapper.idReporte("marca_modelo_123_2020-2022", "Capacidad de aceite")
    val id2 = FichaMapper.idReporte("marca_modelo_123_2020-2022", "  capacidad de aceite ")
    assertEquals("marca-modelo-123-2020-2022__capacidad-de-aceite", id1)
    assertEquals(id1, id2)
  }

  @Test
  fun `extrae el cilindraje en cc`() {
    assertEquals(123, FichaMapper.cilindrajeCc("123cc"))
    assertEquals(123, FichaMapper.cilindrajeCc(" 123 CC "))
    assertNull(FichaMapper.cilindrajeCc("sin dato"))
  }

  @Test
  fun `elige la ficha cuyo rango incluye el anio y sino la del rango mas cercano`() {
    val docs = listOf(doc(2020, 2022), doc(2024, 2025))

    val exacta = FichaMapper.seleccionarConCercania(docs, 2022)!!
    assertTrue(exacta.exacta)
    assertEquals(2020, (exacta.documento["anio_desde"] as Number).toInt())

    val cercanaAnterior = FichaMapper.seleccionarConCercania(docs, 2019)!!
    assertFalse(cercanaAnterior.exacta)
    assertEquals(2020, (cercanaAnterior.documento["anio_desde"] as Number).toInt())

    // Empate a distancia 1 entre 2022 y 2024: debe ganar la más reciente (2024-2025).
    val empate = FichaMapper.seleccionarConCercania(docs, 2023)!!
    assertFalse(empate.exacta)
    assertEquals(2024, (empate.documento["anio_desde"] as Number).toInt())

    assertNull(FichaMapper.seleccionarConCercania(emptyList(), 2021))
    assertNull(FichaMapper.seleccionarConCercania(docs, 0))
  }

  @Test
  fun `no elige fichas sin publicar`() {
    assertNull(FichaMapper.seleccionar(listOf(doc(estado = "pendiente")), 2021))
  }

  @Test
  fun `convierte la ficha conservando la pagina de cada dato y metadatos de aproximacion`() {
    val r = FichaMapper.toResponse(
        doc(docId = "marca-prueba_modelo-x-1_123_2020-2022"),
        aproximada = true,
        anioConsultado = 2023
    )
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
    assertEquals(2023, r.moto_info?.anio)
    assertTrue(r.manual?.aproximada == true)
    assertEquals(2023, r.manual?.anio_consultado)
    assertEquals("marca-prueba_modelo-x-1_123_2020-2022", r.manual?.ficha_id)
  }

  @Test
  fun `la ficha de manual se puede guardar y volver a leer`() {
    val adapter = com.squareup.moshi.Moshi.Builder().build().adapter(DiagnosticResponse::class.java)
    val original = FichaMapper.toResponse(doc(), aproximada = true, anioConsultado = 2024)
    assertEquals(original, adapter.fromJson(adapter.toJson(original)))
  }
}
