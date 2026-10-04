package com.mascill.healthtrack.domain.di

import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.domain.usecase.AcceptGattUseCase
import com.mascill.healthtrack.domain.usecase.AcceptTransmissionUseCase
import com.mascill.healthtrack.domain.usecase.ApplyBroadcastWeightUseCase
import com.mascill.healthtrack.domain.usecase.CalculateReportUseCase
import com.mascill.healthtrack.domain.usecase.CloseMeasurementUseCase
import com.mascill.healthtrack.domain.usecase.DisplayMeasurementUseCase
import com.mascill.healthtrack.domain.usecase.MeasurementStateUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional

/**
 * Provides the measurement use cases.
 * Room binds [MeasurementRepository], so [provideCloseMeasurementUseCase] saves a finished report.
 */
@Module
@InstallIn(SingletonComponent::class)
class UseCaseModule {
    /**
     * Provides [CalculateReportUseCase].
     */
    @Provides
    fun provideCalculateReportUseCase(): CalculateReportUseCase = CalculateReportUseCase()

    /**
     * Provides [DisplayMeasurementUseCase].
     */
    @Provides
    fun provideDisplayMeasurementUseCase(): DisplayMeasurementUseCase = DisplayMeasurementUseCase()

    /**
     * Provides [MeasurementStateUseCase].
     */
    @Provides
    fun provideMeasurementStateUseCase(): MeasurementStateUseCase = MeasurementStateUseCase()

    /**
     * Provides [CloseMeasurementUseCase].
     * An empty [repository] skips save.
     */
    @Provides
    fun provideCloseMeasurementUseCase(
        calculateReport: CalculateReportUseCase,
        repository: Optional<MeasurementRepository>,
    ): CloseMeasurementUseCase = CloseMeasurementUseCase(
        calculateReport = calculateReport,
        repository = repository.orElse(null),
    )

    /**
     * Provides [AcceptTransmissionUseCase].
     */
    @Provides
    fun provideAcceptTransmissionUseCase(
        closeMeasurement: CloseMeasurementUseCase,
    ): AcceptTransmissionUseCase = AcceptTransmissionUseCase(
        closeMeasurement = closeMeasurement,
    )

    /**
     * Provides [AcceptGattUseCase].
     */
    @Provides
    fun provideAcceptGattUseCase(
        closeMeasurement: CloseMeasurementUseCase,
    ): AcceptGattUseCase = AcceptGattUseCase(
        closeMeasurement = closeMeasurement,
    )

    /**
     * Provides [ApplyBroadcastWeightUseCase].
     */
    @Provides
    fun provideApplyBroadcastWeightUseCase(
        closeMeasurement: CloseMeasurementUseCase,
    ): ApplyBroadcastWeightUseCase = ApplyBroadcastWeightUseCase(
        closeMeasurement = closeMeasurement,
    )
}
