package com.mascill.healthtrack.domain.model

open class FrameFormatError(message: String) : IllegalArgumentException(message)

class EncryptedBroadcastError(message: String) : FrameFormatError(message)

class UnsupportedGatt(message: String) : FrameFormatError(message)

class IncompleteGatt(message: String) : Exception(message)
