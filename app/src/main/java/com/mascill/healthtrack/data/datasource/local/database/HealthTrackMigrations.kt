package com.mascill.healthtrack.data.datasource.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the body values used for a finished measurement.
 * Rows saved before this migration leave both columns empty.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE measurement ADD COLUMN height_cm REAL")
        db.execSQL("ALTER TABLE measurement ADD COLUMN age INTEGER")
    }
}
