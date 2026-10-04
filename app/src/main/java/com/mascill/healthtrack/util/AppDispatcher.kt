package com.mascill.healthtrack.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatchers for work that must leave the main thread.
 */
class AppDispatcher(
    val io: CoroutineDispatcher,
    val main: CoroutineDispatcher,
    val default: CoroutineDispatcher,
)
