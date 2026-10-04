package com.mascill.healthtrack.data.di

import com.mascill.healthtrack.util.AppDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/**
 * Provides the process-wide [AppDispatcher].
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    /**
     * IO, main, and default dispatchers for the app process.
     */
    @Provides
    @Singleton
    fun provideAppDispatcher(): AppDispatcher = AppDispatcher(
        io = Dispatchers.IO,
        main = Dispatchers.Main,
        default = Dispatchers.Default,
    )
}
