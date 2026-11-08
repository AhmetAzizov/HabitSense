package com.azizov.habitsense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azizov.habitsense.data.local.HabitSenseDatabase
import com.azizov.habitsense.data.local.entity.HabitEntity
import com.azizov.habitsense.data.local.entity.HabitLogEntity
import com.azizov.habitsense.data.repository.DailyDataRepository
import com.azizov.habitsense.data.repository.HabitPredictionDatasetRepository
import com.azizov.habitsense.data.synthetic.SyntheticDataConfig
import com.azizov.habitsense.data.synthetic.SyntheticDataProfile
import com.azizov.habitsense.data.synthetic.SyntheticDataGenerator
import com.azizov.habitsense.domain.ai.StrategyExecutor
import com.azizov.habitsense.domain.ai.StrategyFactory
import com.azizov.habitsense.domain.ai.StrategyMode
import com.azizov.habitsense.domain.ai.baseline.HeuristicDayClusterer
import com.azizov.habitsense.domain.ai.complex.TfliteHabitPredictor
import com.azizov.habitsense.domain.ai.dataset.HabitPredictionDatasetBuilder
import com.azizov.habitsense.domain.model.DailyData
import com.azizov.habitsense.domain.model.HabitPredictionFeatureSchema
import com.azizov.habitsense.domain.util.DateStringUtils
import com.azizov.habitsense.support.PipelineTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrategyPipelineInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: HabitSenseDatabase
    private lateinit var dailyDataRepository: DailyDataRepository
    private lateinit var syntheticDataGenerator: SyntheticDataGenerator
    private lateinit var strategyFactory: StrategyFactory
    private lateinit var strategyExecutor: StrategyExecutor
    private lateinit var datasetRepository: HabitPredictionDatasetRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, HabitSenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dailyDataRepository = DailyDataRepository(
            habitDao = database.habitDao(),
            habitLogDao = database.habitLogDao(),
            moodLogDao = database.moodLogDao(),
            expenseLogDao = database.expenseLogDao()
        )
        syntheticDataGenerator = SyntheticDataGenerator(
            habitDao = database.habitDao(),
            habitLogDao = database.habitLogDao(),
            moodLogDao = database.moodLogDao(),
            expenseLogDao = database.expenseLogDao()
        )
        strategyFactory = StrategyFactory(context)
        strategyExecutor = StrategyExecutor(database.experimentRunDao())
        datasetRepository = HabitPredictionDatasetRepository(dailyDataRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- Smoke: components run without crashing ---

    @Test
    fun enrichedSynthetic_seedsRoom_andRespectsActiveHabits() = runBlocking {
        seedShortEnrichedDataset()

        val earlyDay = dailyDataRepository.buildDailyData("2026-01-05")
        assertTrue(earlyDay.isHabitActive(1L))
        assertTrue(earlyDay.activeHabitIds.size < 4)

        val habitIds = dailyDataRepository.getHabitIds()
        assertEquals(4, habitIds.size)
    }

    @Test
    fun datasetBuilder_producesFixedSizeLabeledRows() = runBlocking {
        seedShortEnrichedDataset()

        val examples = datasetRepository.buildExamples("2026-01-15", "2026-03-31")
        assertTrue(examples.isNotEmpty())
        assertEquals(HabitPredictionFeatureSchema.DIMENSION, examples.first().features.size)
    }

    @Test
    fun baselineStrategies_runOnRepositoryData() = runBlocking {
        seedShortEnrichedDataset()
        val habitIds = dailyDataRepository.getHabitIds()
        val dailyRange = dailyDataRepository.buildDailyDataRange("2026-01-01", "2026-03-31")
        val targetDate = dailyRange.last().dateString
        val history = dailyRange.dropLast(1)

        val prediction = strategyFactory.habitPredictor(StrategyMode.BASELINE).predict(
            history = history,
            targetDateString = targetDate,
            habitIds = habitIds
        )
        assertEquals(habitIds.size, prediction.predictions.size)
        assertTrue(prediction.predictions.all { it.probabilityComplete in 0f..1f })

        val clusters = strategyFactory.dayClusterer(StrategyMode.BASELINE).cluster(dailyRange, habitIds)
        assertEquals(dailyRange.size, clusters.assignments.size)

        val correlations = strategyFactory.correlationAnalyzer(StrategyMode.BASELINE)
            .analyze(dailyRange, habitIds)
        assertTrue(correlations.pairwiseCorrelations.isNotEmpty())
    }

    @Test
    fun complexStrategies_runIncludingTfliteModel() = runBlocking {
        seedShortEnrichedDataset()
        val habitIds = dailyDataRepository.getHabitIds()
        val dailyRange = dailyDataRepository.buildDailyDataRange("2026-01-01", "2026-03-31")
        val targetDate = dailyRange.last().dateString
        val history = dailyRange.dropLast(1)

        val tflitePredictor = strategyFactory.habitPredictor(StrategyMode.COMPLEX) as TfliteHabitPredictor
        assertTrue("habit_predictor.tflite should load from assets", tflitePredictor.isModelLoaded)

        val prediction = tflitePredictor.predict(
            history = history,
            targetDateString = targetDate,
            habitIds = habitIds
        )
        assertEquals(habitIds.size, prediction.predictions.size)
        assertTrue(prediction.predictions.all { it.probabilityComplete in 0f..1f })
        tflitePredictor.close()

        val clusters = strategyFactory.dayClusterer(StrategyMode.COMPLEX).cluster(dailyRange, habitIds)
        assertEquals(dailyRange.size, clusters.assignments.size)
        assertTrue(clusters.assignments.all { it.clusterId >= 0 })

        val rules = strategyFactory.correlationAnalyzer(StrategyMode.COMPLEX).analyze(dailyRange, habitIds)
        assertNotNull(rules.associationRules)
    }

    @Test
    fun strategyExecutor_logsExperimentRunForComplexPrediction() = runBlocking {
        seedShortEnrichedDataset()
        val habitIds = dailyDataRepository.getHabitIds()
        val startDate = "2026-01-01"
        val endDate = "2026-03-31"
        val history = dailyDataRepository.buildDailyDataRange(
            startDate,
            DateStringUtils.minusDays(endDate, 1)
        )
        val targetDate = endDate
        val predictor = strategyFactory.habitPredictor(StrategyMode.COMPLEX) as TfliteHabitPredictor

        val runsBefore = database.experimentRunDao().getAll().size
        val execution = strategyExecutor.executeAndLog(
            strategyName = predictor.strategyName,
            strategyVersion = predictor.strategyVersion,
            featureSchemaVersion = HabitPredictionFeatureSchema.VERSION,
            inputStartDate = startDate,
            inputEndDate = endDate,
            parameters = mapOf("mode" to StrategyMode.COMPLEX.name)
        ) {
            predictor.predict(history, targetDate, habitIds)
        }

        assertEquals(runsBefore + 1, database.experimentRunDao().getAll().size)
        assertTrue(execution.metrics.executionTimeMs >= 0)
        val logged = database.experimentRunDao().getByStrategy(predictor.strategyName).first()
        assertEquals(startDate, logged.inputStartDate)
        assertEquals(endDate, logged.inputEndDate)
        predictor.close()
    }

    // --- Correctness: matches intended semantics ---

    @Test
    fun datasetExport_labelsMatchRoomGroundTruth() = runBlocking {
        seedShortEnrichedDataset()
        val examples = datasetRepository.buildExamples("2026-01-15", "2026-03-31")
        assertTrue(examples.size >= 50)

        examples.take(40).forEach { example ->
            val targetDay = dailyDataRepository.buildDailyData(example.targetDateString)
            val actual = targetDay.completionFor(example.habitId)
            assertNotNull("Row should only exist for active habits", actual)
            assertEquals(example.label, actual)
        }
    }

    @Test
    fun baselineMovingAverage_matchesIndependentCalculation() = runBlocking {
        seedShortEnrichedDataset()
        val habitIds = dailyDataRepository.getHabitIds()
        val dailyRange = dailyDataRepository.buildDailyDataRange("2026-01-01", "2026-03-31")
        val targetDate = "2026-03-20"
        val history = dailyRange.filter { it.dateString < targetDate }

        val insight = strategyFactory.habitPredictor(StrategyMode.BASELINE).predict(
            history = history,
            targetDateString = targetDate,
            habitIds = habitIds
        )

        habitIds.forEach { habitId ->
            val expected = PipelineTestSupport.expectedMovingAverage(history, habitId)
            val actual = insight.predictions.first { it.habitId == habitId }.probabilityComplete
            assertEquals(expected, actual, 0.0001f)
        }
    }

    @Test
    fun heuristicClusterer_labelsMatchRuleDefinitions() = runBlocking {
        seedShortEnrichedDataset()
        val habitIds = dailyDataRepository.getHabitIds()
        val clusterer = HeuristicDayClusterer()

        val balancedDay = DailyData(
            dateString = "2026-02-01",
            habitCompletionById = mapOf(1L to true, 2L to true),
            activeHabitIds = setOf(1L, 2L),
            mood = 5,
            expenseTotal = 20.0,
            expenseCount = 1
        )
        assertEquals(
            "BalancedDay",
            clusterer.cluster(listOf(balancedDay), habitIds).assignments.single().label
        )

        val stressedDay = DailyData(
            dateString = "2026-02-02",
            habitCompletionById = mapOf(1L to false),
            activeHabitIds = setOf(1L),
            mood = 2,
            expenseTotal = 200.0,
            expenseCount = 2
        )
        assertEquals(
            "StressedSpendingDay",
            clusterer.cluster(listOf(stressedDay), habitIds).assignments.single().label
        )

        val dailyRange = dailyDataRepository.buildDailyDataRange("2026-01-01", "2026-03-31")
        dailyRange.forEach { day ->
            val mood = day.mood
            if (mood != null && mood >= 4 && day.expenseTotal < 50 && day.activeHabitCompletionRate() >= 0.7f) {
                val label = clusterer.cluster(listOf(day), habitIds).assignments.single().label
                assertEquals("BalancedDay", label)
            }
        }
    }

    /**
     * Simulates what Insights will do: for many calendar days, predict from prior history
     * and check predictions against what Room stored for that day.
     */
    @Test
    fun insightsWorkflow_multiDayPrediction_beatsRandomOnEnrichedSeed() = runBlocking {
        seedShortEnrichedDataset()
        val habitIds = dailyDataRepository.getHabitIds()
        val dailyRange = dailyDataRepository.buildDailyDataRange("2026-01-01", "2026-03-31")
        val evalStart = "2026-03-01"

        val baselinePredictor = strategyFactory.habitPredictor(StrategyMode.BASELINE)
        val complexPredictor = strategyFactory.habitPredictor(StrategyMode.COMPLEX) as TfliteHabitPredictor
        assertTrue(complexPredictor.isModelLoaded)

        var baselineCorrect = 0
        var complexCorrect = 0
        var total = 0

        dailyRange
            .filter { it.dateString >= evalStart }
            .forEach { targetDay ->
                val history = dailyRange.filter { it.dateString < targetDay.dateString }
                if (history.isEmpty()) return@forEach

                val baseline = baselinePredictor.predict(history, targetDay.dateString, habitIds)
                val complex = complexPredictor.predict(history, targetDay.dateString, habitIds)

                val (baseOk, baseN) = PipelineTestSupport.predictionAccuracy(baseline.predictions, targetDay)
                val (cxOk, cxN) = PipelineTestSupport.predictionAccuracy(complex.predictions, targetDay)
                baselineCorrect += baseOk
                complexCorrect += cxOk
                total += baseN
                assertEquals(baseN, cxN)
            }

        assertTrue("Need enough active habit-day samples", total >= 40)
        val baselineRate = baselineCorrect.toFloat() / total
        val complexRate = complexCorrect.toFloat() / total
        assertTrue("Baseline should beat random on structured seed (got $baselineRate)", baselineRate > 0.55f)
        assertTrue("TFLite should beat random on structured seed (got $complexRate)", complexRate > 0.55f)

        complexPredictor.close()
    }

    @Test
    fun repository_keepsLaterHabitsInactiveOnEarlierDates() = runBlocking {
        val earlyId = database.habitDao().insert(
            HabitEntity(name = "Early", createdAtDateString = "2026-01-01")
        )
        val lateId = database.habitDao().insert(
            HabitEntity(name = "Late", createdAtDateString = "2026-01-03")
        )
        database.habitLogDao().insert(
            HabitLogEntity(habitId = earlyId, dateString = "2026-01-01", completed = true)
        )
        database.habitLogDao().insert(
            HabitLogEntity(habitId = lateId, dateString = "2026-01-01", completed = true)
        )
        database.habitLogDao().insert(
            HabitLogEntity(habitId = lateId, dateString = "2026-01-03", completed = false)
        )

        val habitIds = dailyDataRepository.getHabitIds()
        val days = dailyDataRepository.buildDailyDataRange("2026-01-01", "2026-01-03")
        assertEquals(listOf(earlyId, lateId), habitIds)

        val beforeLateHabit = days.first { it.dateString == "2026-01-01" }
        assertEquals(setOf(earlyId), beforeLateHabit.activeHabitIds)
        assertEquals(true, beforeLateHabit.completionFor(earlyId))
        assertNull(beforeLateHabit.completionFor(lateId))

        val gapDay = days.first { it.dateString == "2026-01-02" }
        assertEquals(false, gapDay.completionFor(earlyId))
        assertNull(gapDay.completionFor(lateId))

        val lateStart = days.first { it.dateString == "2026-01-03" }
        assertEquals(setOf(earlyId, lateId), lateStart.activeHabitIds)
        assertEquals(false, lateStart.completionFor(earlyId))
        assertEquals(false, lateStart.completionFor(lateId))

        val examples = HabitPredictionDatasetBuilder().build(days, habitIds)
        assertTrue(examples.none { it.habitId == lateId && it.targetDateString < "2026-01-03" })
        val lateExample = examples.single { it.habitId == lateId && it.targetDateString == "2026-01-03" }
        assertFalse(lateExample.label)
        val inactive = HabitPredictionFeatureSchema.FEATURE_NAMES_PER_DAY.indexOf("target_habit_inactive_or_missing")
        val lastDay = HabitPredictionFeatureSchema.WINDOW_DAYS - 1
        assertEquals(1f, lateExample.features[(lastDay - 1) * HabitPredictionFeatureSchema.FEATURES_PER_DAY + inactive], 0.0001f)
        assertEquals(1f, lateExample.features[lastDay * HabitPredictionFeatureSchema.FEATURES_PER_DAY + inactive], 0.0001f)
    }

    private suspend fun seedShortEnrichedDataset() {
        syntheticDataGenerator.generate(
            SyntheticDataConfig(
                seed = 99L,
                startDate = "2026-01-01",
                endDate = "2026-03-31",
                profile = SyntheticDataProfile.ENRICHED,
                habitCreationStaggerDays = 7,
                clearExistingData = true
            )
        )
    }
}
