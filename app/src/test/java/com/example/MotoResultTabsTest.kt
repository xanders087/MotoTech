package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp", sdk = [36])
class MotoResultTabsTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `pestanas Ficha tecnica y Mantenimiento cambian al pulsar`() {
    var selectedTab = 0
    composeTestRule.setContent {
      MyApplicationTheme {
        MotoResultTabBar(
            selectedTab = selectedTab,
            maintenanceCount = 4,
            onSelectTab = { selectedTab = it }
        )
      }
    }

    composeTestRule.onNodeWithText("Ficha técnica").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mantenimiento (4)").assertIsDisplayed()
    composeTestRule.onNodeWithTag("result_tab_mantenimiento").performClick()
    assertEquals(1, selectedTab)
  }

  @Test
  fun `ficha tecnica muestra especificaciones web y pie con fuente oficial`() {
    val specs = EspecificacionesTecnicas(
        cilindraje = DatoWeb(
            texto_original = "249 cc",
            valor_normalizado = "249 cc",
            fuente = "https://www.incolmotos-yamaha.com.co/vehiculo/fz25-abs/",
            capturado = "2026-11-16"
        ),
        capacidad_tanque = DatoWeb(
            texto_original = "14.0",
            valor_normalizado = "14 L",
            fuente = "https://www.incolmotos-yamaha.com.co/vehiculo/fz25-abs/",
            capturado = "2026-11-16"
        )
    )
    val manual = ManualInfo(
        titulo = "Manual Yamaha",
        verificacion_fecha = "2026-11-16"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          TechnicalSpecsCard(
              marca = "Yamaha",
              especificaciones = specs,
              ficha = null,
              manual = manual
          )
        }
      }
    }

    composeTestRule.onNodeWithText("MOTOR").assertIsDisplayed()
    composeTestRule.onNodeWithText("249 cc").assertIsDisplayed()
    composeTestRule.onNodeWithText("DIMENSIONES Y CAPACIDADES").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("14 L").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("ficha_tecnica_footer").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Fuente: sitio oficial de Yamaha · consultado el 16/11/2026")
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun `mantenimiento sin plan oficial muestra estado honesto con Ver manual y Solicitar`() {
    var solicitado = false
    val manual = ManualInfo(url = "https://ejemplo.com/manual.pdf")

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          MaintenanceSection(
              marca = "AKT",
              items = emptyList(),
              proximosResumen = emptyList(),
              manual = manual,
              kmActual = 5000,
              fichaId = "akt_nkd-125_124_2026-2026",
              reportedIds = emptySet(),
              reportandoCampo = null,
              solicitud = SolicitudEstado.DISPONIBLE,
              onSolicitarPlan = { solicitado = true },
              onOpenSummary = {},
              onRegister = {},
              onFindWorkshop = {},
              onReportar = { _, _, _ -> }
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("sin_plan_oficial_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("AKT aún no publica el plan de mantenimiento de este modelo en su sitio.")
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag("ver_manual_oficial_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("solicitar_plan_button").performScrollTo().performClick()
    assertTrue(solicitado)
  }

  @Test
  fun `mantenimiento con plan oficial muestra proximo servicio y pie con pagina`() {
    val plan = PlanMantenimiento(
        componente = "Aceite de motor",
        intervalo_km = 5000,
        accion = "Cambiar",
        pagina = 42
    )
    val item = ItemMantenimiento(
        plan = plan,
        componente = "Aceite de motor",
        estado = EstadoMantenimiento.SIN_HISTORIAL,
        ultimo = null,
        kmRestantes = null,
        diasRestantes = null
    )
    val resumen = ProximoMantenimientoPreventivo(
        plan = plan,
        componente = "Aceite de motor",
        estado = EstadoMantenimiento.PROXIMO,
        ultimo = null,
        proximoKilometraje = 15000,
        kmRestantes = 400,
        kmRecorridosEnCiclo = 4600,
        progresoCiclo = 0.92f,
        diasRestantes = null,
        basadoEnRegistro = false
    )
    val manual = ManualInfo(
        url = "https://ejemplo.com/manual.pdf",
        fuente_plan = FuentePlanPdf(
            url = "https://ejemplo.com/manual.pdf",
            sha256 = "abcdef1234567890",
            pagina = 42,
            titulo = "Manual oficial"
        )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          MaintenanceSection(
              marca = "Bajaj",
              items = listOf(item),
              proximosResumen = listOf(resumen),
              manual = manual,
              kmActual = 14600,
              fichaId = "bajaj_ns200_199_2026-2026",
              reportedIds = emptySet(),
              reportandoCampo = null,
              solicitud = SolicitudEstado.DISPONIBLE,
              onSolicitarPlan = {},
              onOpenSummary = {},
              onRegister = {},
              onFindWorkshop = {},
              onReportar = { _, _, _ -> }
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("proximo_servicio_tab_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mantenimiento_footer").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Según el manual oficial, pág. 42").performScrollTo().assertIsDisplayed()
  }
}
