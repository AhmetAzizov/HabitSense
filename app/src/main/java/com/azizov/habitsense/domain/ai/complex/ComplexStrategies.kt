package com.azizov.habitsense.domain.ai.complex

import android.content.Context
import com.azizov.habitsense.domain.ai.CorrelationAnalyzer
import com.azizov.habitsense.domain.ai.DayClusterer
import com.azizov.habitsense.domain.ai.HabitPredictor
import com.azizov.habitsense.domain.ai.model.AssociationRule
import com.azizov.habitsense.domain.ai.model.CorrelationInsight
import com.azizov.habitsense.domain.ai.model.DayClusterAssignment
import com.azizov.habitsense.domain.ai.model.DayClusterInsight
import com.azizov.habitsense.domain.ai.model.HabitPrediction
import com.azizov.habitsense.domain.ai.model.HabitPredictionInsight
import com.azizov.habitsense.domain.model.DailyData
import com.azizov.habitsense.domain.model.FeatureSchema
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import kotlin.math.sqrt

class TfliteHabitPredictor(
    context: Context,
    private val modelAssetPath: String = "habit_predictor.tflite",
    private val windowDays: Int = 7
) : HabitPredictor, AutoCloseable {
    override val strategyName: String = "TfliteHabitPredictor"
    override val strategyVersion: String = "1.0"

    private val interpreter: Interpreter? = runCatching {
        val modelBuffer = FileUtil.loadMappedFile(context, modelAssetPath)
        Interpreter(modelBuffer)
    }.getOrNull()

    val isModelLoaded: Boolean get() = interpreter != null

    override fun predict(
        history: List<DailyData>,
        targetDateString: String,
        habitOrder: List<Long>
    ): HabitPredictionInsight {
        val trailing = history.takeLast(windowDays)
        if (interpreter == null || trailing.size < windowDays) {
            return fallbackMovingAverage(trailing, targetDateString, habitOrder)
        }

        val featureDim = FeatureSchema.dimension(habitOrder.size)
        val input = Array(1) { FloatArray(windowDays * featureDim) }
        trailing.forEachIndexed { dayIndex, day ->
            val vector = day.toFloatArray(habitOrder)
            vector.forEachIndexed { featureIndex, value ->
                input[0][dayIndex * featureDim + featureIndex] = value
            }
        }

        val output = Array(1) { FloatArray(habitOrder.size) }
        interpreter.run(input, output)

        val predictions = habitOrder.mapIndexed { index, habitId ->
            val probability = output[0][index].coerceIn(0f, 1f)
            HabitPrediction(
                habitId = habitId,
                probabilityComplete = probability,
                predictedComplete = probability >= 0.5f
            )
        }

        return HabitPredictionInsight(
            targetDateString = targetDateString,
            predictions = predictions,
            strategyName = strategyName,
            strategyVersion = strategyVersion
        )
    }

    private fun fallbackMovingAverage(
        trailing: List<DailyData>,
        targetDateString: String,
        habitOrder: List<Long>
    ): HabitPredictionInsight {
        val predictions = habitOrder.map { habitId ->
            val rate = if (trailing.isEmpty()) {
                0.5f
            } else {
                trailing.count { it.habitCompletionById[habitId] == true }.toFloat() / trailing.size
            }
            HabitPrediction(
                habitId = habitId,
                probabilityComplete = rate,
                predictedComplete = rate >= 0.5f
            )
        }
        return HabitPredictionInsight(
            targetDateString = targetDateString,
            predictions = predictions,
            strategyName = "$strategyName(Fallback)",
            strategyVersion = strategyVersion
        )
    }

    override fun close() {
        interpreter?.close()
    }
}

class KMeansDayClusterer(
    private val k: Int = 4,
    private val maxIterations: Int = 50
) : DayClusterer {
    override val strategyName: String = "KMeans"
    override val strategyVersion: String = "1.0"

    override fun cluster(
        dailyData: List<DailyData>,
        habitOrder: List<Long>
    ): DayClusterInsight {
        if (dailyData.isEmpty()) {
            return DayClusterInsight(
                assignments = emptyList(),
                clusterCount = 0,
                strategyName = strategyName,
                strategyVersion = strategyVersion
            )
        }

        val vectors = dailyData.map { it.toFloatArray(habitOrder) }
        val centroids = initializeCentroids(vectors, k.coerceAtMost(vectors.size))
        var assignments = IntArray(vectors.size)

        repeat(maxIterations) {
            var changed = false
            vectors.forEachIndexed { index, vector ->
                val nearest = nearestCentroid(vector, centroids)
                if (assignments[index] != nearest) {
                    assignments[index] = nearest
                    changed = true
                }
            }
            if (!changed) return@repeat
            for (cluster in centroids.indices) {
                val members = vectors.filterIndexed { idx, _ -> assignments[idx] == cluster }
                if (members.isNotEmpty()) {
                    centroids[cluster] = averageVector(members)
                }
            }
        }

        val clusterAssignments = dailyData.mapIndexed { index, day ->
            val vector = vectors[index]
            val clusterId = assignments[index]
            DayClusterAssignment(
                dateString = day.dateString,
                clusterId = clusterId,
                label = "Cluster$clusterId",
                distanceToCentroid = euclideanDistance(vector, centroids[clusterId])
            )
        }

        return DayClusterInsight(
            assignments = clusterAssignments,
            clusterCount = centroids.size,
            strategyName = strategyName,
            strategyVersion = strategyVersion
        )
    }

    private fun initializeCentroids(vectors: List<FloatArray>, k: Int): Array<FloatArray> {
        return Array(k) { index ->
            vectors[index % vectors.size].copyOf()
        }
    }

    private fun nearestCentroid(vector: FloatArray, centroids: Array<FloatArray>): Int {
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        centroids.forEachIndexed { index, centroid ->
            val distance = euclideanDistance(vector, centroid)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun averageVector(vectors: List<FloatArray>): FloatArray {
        val dim = vectors.first().size
        val avg = FloatArray(dim)
        vectors.forEach { vector ->
            vector.forEachIndexed { index, value -> avg[index] += value }
        }
        val count = vectors.size.toFloat()
        for (i in avg.indices) avg[i] /= count
        return avg
    }

    private fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}

class AprioriCorrelationAnalyzer(
    private val minSupport: Float = 0.15f,
    private val minConfidence: Float = 0.6f
) : CorrelationAnalyzer {
    override val strategyName: String = "AprioriRuleMining"
    override val strategyVersion: String = "1.0"

    override fun analyze(
        dailyData: List<DailyData>,
        habitOrder: List<Long>
    ): CorrelationInsight {
        if (dailyData.isEmpty()) {
            return CorrelationInsight(
                pairwiseCorrelations = emptyList(),
                associationRules = emptyList(),
                strategyName = strategyName,
                strategyVersion = strategyVersion
            )
        }

        val transactions = dailyData.map { day -> dayToItems(day, habitOrder) }
        val itemCount = transactions.flatten().groupingBy { it }.eachCount()
        val total = transactions.size.toFloat()

        val frequentItems = itemCount.filter { (_, count) -> count / total >= minSupport }.keys
        val rules = mutableListOf<AssociationRule>()

        frequentItems.forEach { antecedent ->
            frequentItems.forEach { consequent ->
                if (antecedent == consequent) return@forEach
                val antecedentCount = transactions.count { antecedent in it }.toFloat()
                if (antecedentCount == 0f) return@forEach
                val bothCount = transactions.count { antecedent in it && consequent in it }.toFloat()
                val support = bothCount / total
                val confidence = bothCount / antecedentCount
                val consequentSupport = itemCount.getValue(consequent) / total
                val lift = if (consequentSupport == 0f) 0f else confidence / consequentSupport

                if (support >= minSupport && confidence >= minConfidence) {
                    rules += AssociationRule(
                        antecedent = listOf(antecedent),
                        consequent = listOf(consequent),
                        support = support,
                        confidence = confidence,
                        lift = lift
                    )
                }
            }
        }

        return CorrelationInsight(
            pairwiseCorrelations = emptyList(),
            associationRules = rules.sortedByDescending { it.lift },
            strategyName = strategyName,
            strategyVersion = strategyVersion
        )
    }

    private fun dayToItems(day: DailyData, habitOrder: List<Long>): Set<String> {
        val items = mutableSetOf<String>()
        habitOrder.forEach { habitId ->
            if (day.habitCompletionById[habitId] == true) {
                items += "habit_${habitId}_done"
            } else {
                items += "habit_${habitId}_miss"
            }
        }
        day.mood?.let { mood ->
            items += if (mood >= 4) "mood_high" else if (mood <= 2) "mood_low" else "mood_mid"
        }
        if (day.expenseTotal > 100) items += "spend_high" else items += "spend_low"
        return items
    }
}
