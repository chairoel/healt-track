package com.mascill.healthtrack.data.util

import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * ISO-8601 timestamp in UTC, stored in `created_at`.
 */
internal fun utcTimestamp(): String = OffsetDateTime.now(ZoneOffset.UTC).toString()
