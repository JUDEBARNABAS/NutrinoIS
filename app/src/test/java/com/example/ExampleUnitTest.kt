package com.example

import com.example.modbus.SoilReading
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCalibration_math_isCorrect() {
    val raw = SoilReading(
        humidityPercent = 40.0,
        temperatureCelsius = 20.0,
        electricalConductivity = 100,
        pH = 6.0,
        nitrogen = 50,
        phosphorus = 50,
        potassium = 50,
        salinityTds = 50
    )

    val hOffset = 2.5
    val tOffset = -1.2
    val ecMult = 1.2
    val phOff = 0.5
    val nMult = 1.5
    val pMult = 0.8
    val kMult = 1.1
    val salMult = 1.0

    val calHumidity = (raw.humidityPercent + hOffset).coerceIn(0.0, 100.0)
    val calTemp = raw.temperatureCelsius + tOffset
    val calEc = (raw.electricalConductivity * ecMult).toInt().coerceAtLeast(0)
    val calPh = (raw.pH + phOff).coerceIn(0.0, 14.0)
    val calN = (raw.nitrogen * nMult).toInt().coerceAtLeast(0)
    val calP = (raw.phosphorus * pMult).toInt().coerceAtLeast(0)
    val calK = (raw.potassium * kMult).toInt().coerceAtLeast(0)
    val calSal = (raw.salinityTds * salMult).toInt().coerceAtLeast(0)

    val calibrated = SoilReading(
        humidityPercent = Math.round(calHumidity * 10.0) / 10.0,
        temperatureCelsius = Math.round(calTemp * 10.0) / 10.0,
        electricalConductivity = calEc,
        pH = Math.round(calPh * 10.0) / 10.0,
        nitrogen = calN,
        phosphorus = calP,
        potassium = calK,
        salinityTds = calSal
    )

    assertEquals(42.5, calibrated.humidityPercent, 0.01)
    assertEquals(18.8, calibrated.temperatureCelsius, 0.01)
    assertEquals(120, calibrated.electricalConductivity)
    assertEquals(6.5, calibrated.pH, 0.01)
    assertEquals(75, calibrated.nitrogen)
    assertEquals(40, calibrated.phosphorus)
    assertEquals(55, calibrated.potassium)
    assertEquals(50, calibrated.salinityTds)
  }

  @Test
  fun npkPumpController_triggersOnlyDeficientNutrients() {
    val reading = SoilReading(
        humidityPercent = 45.0,
        temperatureCelsius = 22.0,
        electricalConductivity = 300,
        pH = 6.5,
        nitrogen = 20,    // below 40 -> needs adding
        phosphorus = 18,  // >= 15 -> optimal
        potassium = 40,   // below 80 -> needs adding
        salinityTds = 150
    )

    val plan = com.example.bluetooth.NpkPumpController.evaluate(reading)

    assertTrue(plan.anyPumpNeeded)
    assertTrue(plan.nitrogen.needsAdding)
    assertFalse(plan.phosphorus.needsAdding)
    assertTrue(plan.potassium.needsAdding)
    assertTrue(plan.nitrogen.durationMs in 2000..8000)
    assertEquals(0, plan.phosphorus.durationMs)
    assertTrue(plan.potassium.durationMs in 2000..8000)
    assertEquals(
        "DOSE:N:${plan.nitrogen.durationMs},P:0,K:${plan.potassium.durationMs}",
        plan.toArduinoDoseCommand()
    )
  }

  @Test
  fun npkPumpController_returnsNoPumpsWhenAllOptimal() {
    val reading = SoilReading(
        humidityPercent = 45.0,
        temperatureCelsius = 22.0,
        electricalConductivity = 300,
        pH = 6.5,
        nitrogen = 60,
        phosphorus = 25,
        potassium = 120,
        salinityTds = 150
    )

    val plan = com.example.bluetooth.NpkPumpController.evaluate(reading)

    assertFalse(plan.anyPumpNeeded)
    assertEquals("DOSE:N:0,P:0,K:0", plan.toArduinoDoseCommand())
  }
}
