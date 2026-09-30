package com.mascill.healthtrack.data.di

import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.data.repository.gatt.GattParser
import com.mascill.healthtrack.data.util.TxConst
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    /**
     * Session parser kept by [ScaleGattRepositoryImpl.parse].
     * Split commands 18, 19, and 20 finish on a later call to this instance.
     */
    @Provides
    @Singleton
    fun gattParser(): GattParser = GattParser()

    /** Advertisement payloads accepted as a scale frame: [TxConst.HEADER_CA] and [TxConst.HEADER_C0]. */
    @Provides
    @ScaleFrameHeaders
    fun scaleHeaders(): IntArray = intArrayOf(TxConst.HEADER_CA, TxConst.HEADER_C0)
}
