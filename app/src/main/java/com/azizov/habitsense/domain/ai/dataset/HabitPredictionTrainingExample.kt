package com.azizov.habitsense.domain.ai.dataset

import com.azizov.habitsense.domain.model.HabitPredictionFeatureSchema

/**
 * One supervised row: trailing history encoded as [features], label is completion on [targetDateString].
 */
data class HabitPredictionTrainingExample(
    val targetDateString: String,
    val habitId: Long,
    val label: Boolean,
    val features: FloatArray,
    val featureSchemaVersion: Int = HabitPredictionFeatureSchema.VERSION
) {
    init {
        require(features.size == HabitPredictionFeatureSchema.DIMENSION) {
            "Expected ${HabitPredictionFeatureSchema.DIMENSION} features, got ${features.size}"
        }
    }

    val labelAsFloat: Float get() = if (label) 1f else 0f
}
