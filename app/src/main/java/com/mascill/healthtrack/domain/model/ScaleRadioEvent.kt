package com.mascill.healthtrack.domain.model

/**
 * One event from the scale radio.
 * Connection events carry the epoch returned by [com.mascill.healthtrack.domain.repository.ScaleRadioRepository.connect].
 */
sealed interface ScaleRadioEvent {
    /**
     * One accepted advertisement payload.
     * [payload] is the scale bytes, not the raw manufacturer block.
     */
    data class Advertisement(
        val address: String,
        val payload: List<Byte>,
        val connectable: Boolean,
    ) : ScaleRadioEvent

    /**
     * One RX notification for [epoch].
     */
    data class Notification(
        val epoch: Int,
        val value: List<Byte>,
    ) : ScaleRadioEvent

    /**
     * RX notify is enabled for [epoch].
     */
    data class NotifyReady(val epoch: Int) : ScaleRadioEvent

    /**
     * The link dropped after a clean disconnect for [epoch].
     */
    data class LinkLost(val epoch: Int) : ScaleRadioEvent

    /**
     * The Chipsea service or RX characteristic is absent for [epoch].
     */
    data class NotifyMissing(val epoch: Int) : ScaleRadioEvent

    /**
     * Connect, discovery, or the notify descriptor failed for [epoch].
     */
    data class ConnectFailed(val epoch: Int) : ScaleRadioEvent

    /**
     * The scanner reported a failure other than an already-started scan.
     */
    object ScanFailed : ScaleRadioEvent
}
