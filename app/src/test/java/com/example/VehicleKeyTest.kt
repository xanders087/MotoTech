package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VehicleKeyTest {
  @Test
  fun `misma moto con distinta escritura da la misma clave`() {
    assertEquals(
        VehicleKey.of("Yamaha", "MT-07", "689cc", "2023"),
        VehicleKey.of("  yamaha ", "mt-07", "689 CC", "2023 "))
  }

  @Test
  fun `otro anio es otra moto`() {
    assertNotEquals(
        VehicleKey.of("Yamaha", "MT-07", "689cc", "2023"),
        VehicleKey.of("Yamaha", "MT-07", "689cc", "2019"))
  }
}
