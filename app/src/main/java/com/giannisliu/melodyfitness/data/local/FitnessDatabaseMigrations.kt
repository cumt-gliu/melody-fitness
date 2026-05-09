package com.giannisliu.melodyfitness.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object FitnessDatabaseMigrations {
    val MIGRATION_1_2_SQL = listOf(
        "ALTER TABLE body_metrics ADD COLUMN bodyFatPercentage REAL",
        "ALTER TABLE body_metrics ADD COLUMN waistCm REAL",
        "ALTER TABLE body_metrics ADD COLUMN sleepHours REAL",
        "ALTER TABLE body_metrics ADD COLUMN fatigueScore INTEGER",
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach(db::execSQL)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
