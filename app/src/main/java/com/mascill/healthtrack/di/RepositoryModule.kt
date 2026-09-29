package com.mascill.healthtrack.di

import com.mascill.healthtrack.data.repository.ScaleBroadcastRepositoryImpl
import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.domain.repository.ScaleBroadcastRepository
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
    abstract fun bindScaleBroadcastRepository(
        impl: ScaleBroadcastRepositoryImpl,
    ): ScaleBroadcastRepository

    @Binds
    @Singleton
    abstract fun bindScaleGattRepository(
        impl: ScaleGattRepositoryImpl,
    ): ScaleGattRepository
}
