package com.example

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticParserTest {
  private val adapter = Moshi.Builder().build().adapter(DiagnosticResponse::class.java)

  @Test
  fun `lee una respuesta con datos faltantes en null`() {
    val json = """
      ```json
      {"status":"success","moto_info":{"marca":"Yamaha","modelo":"MT-07","cilindraje":"689cc","anio":2023},
       "ficha_tecnica":{
         "capacidad_aceite":{"valor":"3.0 L","fuente_titulo":"Manual","fuente_dominio":"yamaha-motor.com","tipo_fuente":"manual_fabricante"},
         "holgura_cadena":{"valor":null,"fuente_titulo":null,"fuente_dominio":null,"tipo_fuente":null}},
       "plan_mantenimiento_periodico":[{"componente":"Aceite de motor","intervalo_km":10000,"intervalo_meses":null,"accion":"Cambiar"}]}
      ```
    """.trimIndent()
    val r = DiagnosticParser.parse(json, adapter)
    assertNotNull(r)
    assertEquals("3.0 L", r!!.ficha_tecnica?.capacidad_aceite?.valor)
    assertNull(r.ficha_tecnica?.holgura_cadena?.valor)
    assertNull(r.ficha_tecnica?.liquido_frenos)
    assertEquals(10000, r.plan_mantenimiento_periodico?.single()?.intervalo_km)
    assertNull(r.plan_mantenimiento_periodico?.single()?.intervalo_meses)
  }

  @Test
  fun `lee una respuesta needs_info sin ficha`() {
    val r = DiagnosticParser.parse(
        """{"status":"needs_info","mensaje":"No identifico el modelo","campos_faltantes":["modelo"]}""", adapter)
    assertEquals("needs_info", r?.status)
    assertEquals(listOf("modelo"), r?.campos_faltantes)
    assertNull(r?.ficha_tecnica)
  }

  @Test
  fun `texto sin json devuelve null`() {
    assertNull(DiagnosticParser.parse("No encontré información.", adapter))
    assertNull(DiagnosticParser.parse("{ esto no es json }", adapter))
  }

  @Test
  fun `la ficha guardada conserva las fuentes`() {
    val original = DiagnosticResponse(status = "success", fuentes = listOf(FuenteConsultada("yamaha-motor.com", "https://x")))
    val vuelta = adapter.fromJson(adapter.toJson(original))
    assertEquals(original, vuelta)
  }
}
