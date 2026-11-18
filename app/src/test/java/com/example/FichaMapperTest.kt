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

  private fun docV3(
      tituloPdf: String? = "Manual Oficial Prueba",
      incluirPlan: Boolean = true,
      incluirServicio: Boolean = true
  ): Map<String, Any?> = buildMap {
    put("_docId", "marca-prueba_modelo-v3_249_2026-2027")
    put("marca", "Marca Prueba")
    put("modelo", "Modelo V3")
    put("cilindraje_cc", 249L)
    put("anio_desde", 2026L)
    put("anio_hasta", 2027L)
    put("anio_origen", "deteccion")
    put("estado", "publicada")
    put("clave", "marca prueba|modelo v3|249")
    put("url_manual", "https://ejemplo.com/manual_v3.pdf")
    put("verificacion", mapOf(
        "metodo" to "extraccion determinista v1",
        "fecha" to "2026-11-16"
    ))
    put("ficha_tecnica", mapOf(
        "cilindraje" to mapOf(
            "texto_original" to "249 cc",
            "valor_normalizado" to "249 cc",
            "fuente" to "https://ejemplo.com/modelo-v3",
            "capturado" to "2026-11-16"
        ),
        "potencia_maxima" to mapOf(
            "texto_original" to "20,6 HP @ 8.000 rpm",
            "valor_normalizado" to "20.6 HP @ 8000 rpm",
            "fuente" to "https://ejemplo.com/modelo-v3",
            "capturado" to "2026-11-16"
        ),
        "capacidad_tanque" to mapOf(
            "texto_original" to "14.0",
            "valor_normalizado" to "14 L",
            "fuente" to "https://ejemplo.com/modelo-v3",
            "capturado" to "2026-11-16"
        )
    ))
    if (incluirServicio) {
      put("datos_servicio", mapOf(
          "capacidad_aceite" to mapOf("valor" to "1,15 L", "pagina" to 78L)
      ))
    }
    if (incluirPlan) {
      put("plan_mantenimiento", mapOf(
          "fuente" to mapOf(
              "url" to "https://ejemplo.com/manual_v3.pdf",
              "sha256" to "abc123sha256",
              "pagina" to 42L,
              "titulo" to tituloPdf
          ),
          "tareas" to listOf(
              mapOf(
                  "componente" to "Aceite de motor",
                  "accion" to "Cambiar",
                  "intervalo_km" to 5000L,
                  "intervalo_meses" to 6L
              )
          )
      ))
    }
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
  fun `convierte ficha v3 con especificaciones web, datos de servicio y plan con fuente PDF`() {
    val r = FichaMapper.toResponse(docV3(), aproximada = false, anioConsultado = 2026)
    assertEquals("249 cc", r.especificaciones?.cilindraje?.texto_original)
    assertEquals("https://ejemplo.com/modelo-v3", r.especificaciones?.cilindraje?.fuente)
    assertEquals("14 L", r.especificaciones?.capacidad_tanque?.valor_normalizado)
    assertEquals("1,15 L", r.ficha_tecnica?.capacidad_aceite?.valor)
    assertEquals(78, r.ficha_tecnica?.capacidad_aceite?.pagina)

    val tarea = r.plan_mantenimiento_periodico!!.single()
    assertEquals("Aceite de motor", tarea.componente)
    assertEquals(5000, tarea.intervalo_km)
    assertEquals(6, tarea.intervalo_meses)
    // Hereda la página desde plan_mantenimiento.fuente.pagina
    assertEquals(42, tarea.pagina)

    assertEquals("Manual Oficial Prueba", r.manual?.titulo)
    assertEquals("https://ejemplo.com/manual_v3.pdf", r.manual?.url)
    assertEquals("abc123sha256", r.manual?.fuente_plan?.sha256)
    assertEquals(42, r.manual?.fuente_plan?.pagina)
    assertEquals("deteccion", r.manual?.anio_origen)
    assertEquals("extraccion determinista v1", r.manual?.verificacion_metodo)
    assertEquals("2026-11-16", r.manual?.verificacion_fecha)
  }

  @Test
  fun `ignora titulo untitled en plan v3 y funciona sin plan oficial`() {
    val conUntitled = FichaMapper.toResponse(docV3(tituloPdf = "untitled"), anioConsultado = 2026)
    assertNull(conUntitled.manual?.titulo)

    val sinPlan = FichaMapper.toResponse(
        docV3(incluirPlan = false, incluirServicio = false),
        anioConsultado = 2027
    )
    assertTrue(sinPlan.plan_mantenimiento_periodico.isNullOrEmpty())
    assertNull(sinPlan.ficha_tecnica)
    assertEquals("https://ejemplo.com/manual_v3.pdf", sinPlan.manual?.url)
    assertEquals("20,6 HP @ 8.000 rpm", sinPlan.especificaciones?.potencia_maxima?.texto_original)
  }

  @Test
  fun `la ficha de manual y v3 se puede guardar y volver a leer`() {
    val adapter = com.squareup.moshi.Moshi.Builder().build().adapter(DiagnosticResponse::class.java)
    val legacy = FichaMapper.toResponse(doc(), aproximada = true, anioConsultado = 2024)
    assertEquals(legacy, adapter.fromJson(adapter.toJson(legacy)))

    val v3 = FichaMapper.toResponse(docV3(), aproximada = false, anioConsultado = 2026)
    assertEquals(v3, adapter.fromJson(adapter.toJson(v3)))
  }
}
