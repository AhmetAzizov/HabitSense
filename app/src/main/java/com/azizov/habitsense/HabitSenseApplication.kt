package com.azizov.habitsense

import android.app.Application
import com.azizov.habitsense.data.local.DatabaseProvider
import com.azizov.habitsense.data.local.HabitSenseDatabase
import com.azizov.habitsense.data.repository.DailyDataRepository
import com.azizov.habitsense.data.repository.HabitPredictionDatasetRepository
import com.azizov.habitsense.data.synthetic.SyntheticDataGenerator
import com.azizov.habitsense.domain.ai.StrategyExecutor
import com.azizov.habitsense.domain.ai.StrategyFactory

class HabitSenseApplication : Application() {
    lateinit var database: HabitSenseDatabase
        private set

    lateinit var dailyDataRepository: DailyDataRepository
        private set

    lateinit var syntheticDataGenerator: SyntheticDataGenerator
        private set

    lateinit var strategyFactory: StrategyFactory
        private set

    lateinit var strategyExecutor: StrategyExecutor
        private set

    lateinit var habitPredictionDatasetRepository: HabitPredictionDatasetRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = DatabaseProvider.get(this)
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
        strategyFactory = StrategyFactory(this)
        strategyExecutor = StrategyExecutor(database.experimentRunDao())
        habitPredictionDatasetRepository = HabitPredictionDatasetRepository(dailyDataRepository)
    }
}
