package com.giannisliu.melodyfitness.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessDatabaseMigrationsTest {

    @Test
    fun migration_1_2_adds_extended_body_metric_columns_without_destructive_sql() {
        assertEquals(1, FitnessDatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, FitnessDatabaseMigrations.MIGRATION_1_2.endVersion)

        val sql = FitnessDatabaseMigrations.MIGRATION_1_2_SQL
        assertTrue(sql.contains("ALTER TABLE body_metrics ADD COLUMN bodyFatPercentage REAL"))
        assertTrue(sql.contains("ALTER TABLE body_metrics ADD COLUMN waistCm REAL"))
        assertTrue(sql.contains("ALTER TABLE body_metrics ADD COLUMN sleepHours REAL"))
        assertTrue(sql.contains("ALTER TABLE body_metrics ADD COLUMN fatigueScore INTEGER"))

        val joinedSql = sql.joinToString(separator = "\n").uppercase()
        assertFalse(joinedSql.contains("DROP TABLE"))
        assertFalse(joinedSql.contains("DELETE FROM"))
    }
}
