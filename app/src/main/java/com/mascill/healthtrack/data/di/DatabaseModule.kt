package com.mascill.healthtrack.data.di

import android.content.Context
import androidx.room.Room
import com.mascill.healthtrack.data.datasource.local.BodyDao
import com.mascill.healthtrack.data.datasource.local.HealthTrackDatabase
import com.mascill.healthtrack.data.datasource.local.MeasurementDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Room database and its DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * Database file [DATABASE_NAME] with the `body` and `measurement` tables.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): HealthTrackDatabase = Room.databaseBuilder(
        context,
        HealthTrackDatabase::class.java,
        DATABASE_NAME,
    ).build()

    /**
     * DAO for the `body` table.
     */
    @Provides
    fun provideBodyDao(database: HealthTrackDatabase): BodyDao = database.bodyDao()

    /**
     * DAO for the `measurement` table.
     */
    @Provides
    fun provideMeasurementDao(database: HealthTrackDatabase): MeasurementDao =
        database.measurementDao()

    private const val DATABASE_NAME = "healthtrack.db"
}
