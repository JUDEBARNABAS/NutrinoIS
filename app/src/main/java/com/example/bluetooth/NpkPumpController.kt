package com.example.bluetooth

import com.example.modbus.SoilReading

/**
 * Thresholds and dosing duration parameters for N, P, K nutrient pumps.
 * Default target minimums match the Ground Truth Optimal Targets in the dashboard:
 * - Nitrogen (N): >= 40 mg/kg
 * - Phosphorus (P): >= 15 mg/kg
 * - Potassium (K): >= 80 mg/kg
 */
data class NpkThresholds(
    val minNitrogen: Int = 40,
    val minPhosphorus: Int = 15,
    val minPotassium: Int = 80,
    val baseDoseMs: Int = 2000,
    val maxDoseMs: Int = 8000
)

/**
 * Per-nutrient dosing assessment derived from a Ground Truth reading.
 */
data class NutrientPumpDecision(
    val nutrientCode: Char,
    val nutrientName: String,
    val currentValue: Int,
    val targetMin: Int,
    val needsAdding: Boolean,
    val deficit: Int,
    val durationMs: Int
)

/**
 * Complete N-P-K dosing plan produced when Ground Truth telemetry is read.
 */
data class NpkDosingPlan(
    val nitrogen: NutrientPumpDecision,
    val phosphorus: NutrientPumpDecision,
    val potassium: NutrientPumpDecision,
    val sourceLabel: String,
    val evaluatedAt: Long = System.currentTimeMillis()
) {
    val anyPumpNeeded: Boolean
        get() = nitrogen.needsAdding || phosphorus.needsAdding || potassium.needsAdding

    fun nutrientsToAdd(): List<NutrientPumpDecision> =
        listOf(nitrogen, phosphorus, potassium).filter { it.needsAdding }

    /**
     * Formats the serial command sent to the Arduino + HC-05 relay controller.
     * Example: "DOSE:N:4000,P:2500,K:0"
     */
    fun toArduinoDoseCommand(): String =
        "DOSE:N:${nitrogen.durationMs},P:${phosphorus.durationMs},K:${potassium.durationMs}"

    fun summaryText(): String {
        val needed = nutrientsToAdd()
        if (needed.isEmpty()) {
            return "All N-P-K levels meet Ground Truth targets. No pump dosing needed."
        }
        return needed.joinToString(" • ") { decision ->
            val sec = decision.durationMs / 1000.0
            "${decision.nutrientCode}: +${decision.deficit} mg/kg (${String.format(java.util.Locale.US, "%.1fs", sec)})"
        }
    }
}

object NpkPumpController {

    fun evaluate(
        reading: SoilReading,
        thresholds: NpkThresholds = NpkThresholds(),
        sourceLabel: String = "Ground Truth"
    ): NpkDosingPlan {
        return evaluateValues(
            nitrogen = reading.nitrogen,
            phosphorus = reading.phosphorus,
            potassium = reading.potassium,
            thresholds = thresholds,
            sourceLabel = sourceLabel
        )
    }

    fun evaluateValues(
        nitrogen: Int,
        phosphorus: Int,
        potassium: Int,
        thresholds: NpkThresholds = NpkThresholds(),
        sourceLabel: String = "Ground Truth"
    ): NpkDosingPlan {
        return NpkDosingPlan(
            nitrogen = evaluateSingle(
                code = 'N',
                name = "Nitrogen (N)",
                current = nitrogen,
                targetMin = thresholds.minNitrogen,
                baseDoseMs = thresholds.baseDoseMs,
                maxDoseMs = thresholds.maxDoseMs
            ),
            phosphorus = evaluateSingle(
                code = 'P',
                name = "Phosphorus (P)",
                current = phosphorus,
                targetMin = thresholds.minPhosphorus,
                baseDoseMs = thresholds.baseDoseMs,
                maxDoseMs = thresholds.maxDoseMs
            ),
            potassium = evaluateSingle(
                code = 'K',
                name = "Potassium (K)",
                current = potassium,
                targetMin = thresholds.minPotassium,
                baseDoseMs = thresholds.baseDoseMs,
                maxDoseMs = thresholds.maxDoseMs
            ),
            sourceLabel = sourceLabel
        )
    }

    private fun evaluateSingle(
        code: Char,
        name: String,
        current: Int,
        targetMin: Int,
        baseDoseMs: Int,
        maxDoseMs: Int
    ): NutrientPumpDecision {
        val safeTarget = targetMin.coerceAtLeast(1)
        val safeBase = baseDoseMs.coerceAtLeast(500)
        val safeMax = maxDoseMs.coerceAtLeast(safeBase)

        return if (current < safeTarget) {
            val deficit = safeTarget - current
            val ratio = (deficit.toDouble() / safeTarget.toDouble()).coerceIn(0.0, 1.0)
            val rawMs = safeBase + (ratio * (safeMax - safeBase)).toInt()
            // Snap to clean 500ms increments for deterministic relay timing
            val snappedMs = (((rawMs + 250) / 500) * 500).coerceIn(safeBase, safeMax)
            NutrientPumpDecision(
                nutrientCode = code,
                nutrientName = name,
                currentValue = current,
                targetMin = safeTarget,
                needsAdding = true,
                deficit = deficit,
                durationMs = snappedMs
            )
        } else {
            NutrientPumpDecision(
                nutrientCode = code,
                nutrientName = name,
                currentValue = current,
                targetMin = safeTarget,
                needsAdding = false,
                deficit = 0,
                durationMs = 0
            )
        }
    }
}
