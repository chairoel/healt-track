package com.mascill.healthtrack.di

import com.mascill.healthtrack.data.repository.ScaleTransmissionRepositoryImpl
import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.domain.repository.ScaleTransmissionRepository
import com.mascill.healthtrack.domain.repository.ScaleGattRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindScaleTransmissionRepository(
        impl: ScaleTransmissionRepositoryImpl,
    ): ScaleTransmissionRepository

    @Binds
    @Singleton
    abstract fun bindScaleGattRepository(
        impl: ScaleGattRepositoryImpl,
    ): ScaleGattRepository
}
