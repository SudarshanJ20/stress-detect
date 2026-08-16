package com.stressdetect.data

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Check-in history: the only thing this app remembers about what a person told it.
 *
 * Real and demo check-ins are kept apart. A rehearsal must not leave a mark on someone's
 * actual trend, and a demo needs its own history to show — so both are stored and the
 * caller reads back whichever matches the mode it is in.
 */
class CheckInRepository(
    private val context: Context,
    private val database: StressDetectDatabase = StressDetectDatabase.get(context),
) {

    data class Entry(
        val takenAt: LocalDate,
        val score: Int,
    )

    suspend fun record(score: Int, isDemo: Boolean) {
        database.checkInDao().insert(
            CheckInEntity(
                takenAtUtc = Instant.now().epochSecond,
                score = score,
                isDemo = isDemo,
            )
        )
    }

    suspend fun history(isDemo: Boolean, zone: ZoneId = ZoneId.systemDefault()): List<Entry> =
        database.checkInDao().history(isDemo).map {
            Entry(
                takenAt = Instant.ofEpochSecond(it.takenAtUtc).atZone(zone).toLocalDate(),
                score = it.score,
            )
        }

    /** Offered in About, so a participant can remove their own history without uninstalling. */
    suspend fun deleteAll(): Int = database.checkInDao().deleteAll()
}
