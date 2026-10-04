package com.mascill.healthtrack.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the `healthtrack_prefs` DataStore. The file has no required keys.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /**
     * Preferences DataStore named [PREFS_NAME].
     */
    @Provides
    @Singleton
    fun providePreferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(PREFS_NAME) },
    )

    private const val PREFS_NAME = "healthtrack_prefs"
}
