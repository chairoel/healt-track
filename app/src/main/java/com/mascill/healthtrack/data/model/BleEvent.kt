package com.mascill.healthtrack.data.model

/**
 * One manufacturer block from an advertisement.
 * [remainder] follows the company id. [BleScanEvent.Advertisement] keeps these in scanner order.
 */
data class BleManufacturerBlock(
    val companyId: Int,
    val remainder: List<Byte>,
)

/**
 * Scan result or a scanner failure.
 */
sealed interface BleScanEvent {
    /**
     * One scan result from the radio.
     * [manufacturers] keeps every manufacturer block in that result, in scanner order.
     * [record] is the raw advertisement, used when no manufacturer block is a scale payload.
     */
    data class Advertisement(
        val address: String,
        val connectable: Boolean,
        val manufacturers: List<BleManufacturerBlock>,
        val record: List<Byte>?,
    ) : BleScanEvent

    /**
     * The scanner reported a failure other than an already-started scan.
     */
    object Failed : BleScanEvent
}

/**
 * One GATT event for the active connect.
 */
sealed interface BleConnectEvent {
    /**
     * One RX notification.
     */
    data class Notification(
        val value: List<Byte>,
    ) : BleConnectEvent

    /**
     * RX notify is enabled.
     */
    object NotifyReady : BleConnectEvent

    /**
     * The link dropped after a clean disconnect.
     */
    object LinkLost : BleConnectEvent

    /**
     * The Chipsea service or RX characteristic is absent.
     */
    object NotifyMissing : BleConnectEvent

    /**
     * Connect, discovery, or the notify descriptor failed.
     */
    object Failed : BleConnectEvent
}
