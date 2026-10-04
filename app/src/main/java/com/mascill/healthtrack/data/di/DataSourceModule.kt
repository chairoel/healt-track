package com.mascill.healthtrack.data.di

import com.mascill.healthtrack.data.datasource.BleDataSource
import com.mascill.healthtrack.data.datasource.BleDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds data-source interfaces to the platform classes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {
    /**
     * Phone radio. Callers inject [BleDataSource].
     */
    @Binds
    @Singleton
    abstract fun bindBleDataSource(
        impl: BleDataSourceImpl,
    ): BleDataSource
}
