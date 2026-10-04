package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.BodyData

/**
 * Stores body profiles. Each [save] inserts a new row.
 * [latest] reads the row with the greatest id.
 */
interface BodyRepository {
    /**
     * Inserts [body] and returns the new row id.
     * [BodyData] rejects a sex other than 0 or 1 before this insert.
     */
    suspend fun save(body: BodyData): Int

    /**
     * The newest profile, or null when no row exists.
     */
    suspend fun latest(): BodyData?
}
