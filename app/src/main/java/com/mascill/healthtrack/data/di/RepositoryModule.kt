package com.mascill.healthtrack.data.di

import com.mascill.healthtrack.data.repository.ScaleTransmissionRepositoryImpl
import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.domain.repository.ScaleTransmissionRepository
import com.mascill.healthtrack.domain.repository.ScaleGattRepository
import com.mascill.healthtrack.domain.usecase.CloseMeasurementUseCase
import dagger.Binds
import dagger.BindsOptionalOf
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

    /**
     * Empty until Room binds [MeasurementRepository].
     * [CloseMeasurementUseCase] skips save while this optional is empty.
     */
    @BindsOptionalOf
    abstract fun measurementRepository(): MeasurementRepository
}
