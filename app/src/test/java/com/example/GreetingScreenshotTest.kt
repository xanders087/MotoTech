package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun workshop_card_screenshot() {
    val sampleWorkshop = WorkshopItem(
        id = "W-TEST",
        nombre = "Taller MotoTech Express",
        direccion = "Av. Circunvalar # 45-21",
        distancia = "2.1 km • 6 min en moto",
        calificacion = 4.9,
        totalResenas = 184,
        especialidades = listOf("Ajuste Válvulas", "Sincronización"),
        descuentoConvenio = "15% OFF en mano de obra",
        telefono = "+57 312 450 8921",
        horario = "Abierto Hoy: 8:00 AM - 6:30 PM",
        latitud = 4.6025,
        longitud = -74.0720
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        WorkshopCard(
            workshop = sampleWorkshop,
            onNavigateMaps = {},
            onBook = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/workshop_card.png")
  }
}
