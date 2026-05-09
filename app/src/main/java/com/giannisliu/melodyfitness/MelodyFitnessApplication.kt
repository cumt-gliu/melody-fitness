package com.giannisliu.melodyfitness

import android.app.Application
import androidx.room.Room
import com.giannisliu.melodyfitness.data.local.FitnessDatabase
import com.giannisliu.melodyfitness.data.local.FitnessDatabaseMigrations
import com.giannisliu.melodyfitness.data.repository.OfflineFitnessRepository
import com.giannisliu.melodyfitness.data.settings.SettingsRepository

class MelodyFitnessApplication : Application() {
    val database: FitnessDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            FitnessDatabase::class.java,
            "melody-fitness.db",
        ).addMigrations(*FitnessDatabaseMigrations.ALL).build()
    }

    val repository: OfflineFitnessRepository by lazy {
        OfflineFitnessRepository(
            workoutLogDao = database.workoutLogDao(),
            bodyMetricDao = database.bodyMetricDao(),
            goalDao = database.goalDao(),
            statsDao = database.statsDao(),
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(applicationContext)
    }
}
