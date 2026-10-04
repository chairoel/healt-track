package com.mascill.healthtrack.data.datasource.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences file `healthtrack_prefs`.
 * No key is required. Profiles and measurement rows are not stored here.
 */
@Singleton
class HealthTrackPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * The preferences store. Callers add keys when a preference exists.
     */
    fun store(): DataStore<Preferences> = dataStore
}