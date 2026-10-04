package com.mascill.healthtrack.domain.model

/**
 * One event from the scale radio.
 * Connection events carry the epoch returned by [com.mascill.healthtrack.domain.repository.ScaleRadioRepository.connect].
 */
sealed interface ScaleRadioEvent

/**
 * One accepted advertisement payload.
 */
class ScaleAdvertisement(
    val address: String,
    val payload: ByteArray,
    val connectable: Boolean,
) : ScaleRadioEvent

/**
 * One RX notification for [epoch].
 */
class ScaleNotification(
    val epoch: Int,
    val value: ByteArray,
) : ScaleRadioEvent

/**
 * RX notify is enabled for [epoch].
 */
class ScaleNotifyReady(
    val epoch: Int,
) : ScaleRadioEvent

/**
 * The link dropped after a clean disconnect for [epoch].
 */
class ScaleLinkLost(
    val epoch: Int,
) : ScaleRadioEvent

/**
 * The Chipsea service or RX characteristic is absent for [epoch].
 */
class ScaleNotifyMissing(
    val epoch: Int,
) : ScaleRadioEvent

/**
 * Connect, discovery, or the notify descriptor failed for [epoch].
 */
class ScaleConnectFailed(
    val epoch: Int,
) : ScaleRadioEvent

/**
 * The scanner reported a failure other than an already-started scan.
 */
data object ScaleScanFailed : ScaleRadioEvent
