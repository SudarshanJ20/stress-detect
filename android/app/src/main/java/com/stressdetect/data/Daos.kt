package com.stressdetect.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RawUsageEventDao {
    /** IGNORE on conflict: re-running extraction over an overlapping span must not duplicate. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<RawUsageEventEntity>)

    @Query("SELECT * FROM raw_usage_event WHERE epochSeconds >= :fromUtc AND epochSeconds < :toUtc ORDER BY epochSeconds ASC")
    suspend fun between(fromUtc: Long, toUtc: Long): List<RawUsageEventEntity>

    @Query("SELECT MIN(epochSeconds) FROM raw_usage_event")
    suspend fun earliestEventUtc(): Long?

    @Query("SELECT COUNT(*) FROM raw_usage_event")
    suspend fun count(): Int
}

@Dao
interface LockedIntervalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(intervals: List<LockedIntervalEntity>)

    /** Overlap, not containment — an interval straddling the window edge still counts. */
    @Query("SELECT * FROM locked_interval WHERE endUtc > :fromUtc AND startUtc < :toUtc ORDER BY startUtc ASC")
    suspend fun overlapping(fromUtc: Long, toUtc: Long): List<LockedIntervalEntity>

    @Query("SELECT COUNT(*) FROM locked_interval")
    suspend fun count(): Int
}

@Dao
interface CommEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<CommEventEntity>)

    @Query("SELECT epochSeconds FROM comm_event WHERE kind = :kind AND epochSeconds >= :fromUtc AND epochSeconds < :toUtc ORDER BY epochSeconds ASC")
    suspend fun timestamps(kind: String, fromUtc: Long, toUtc: Long): List<Long>
}

@Dao
interface DailyAppUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(buckets: List<DailyAppUsageEntity>)

    @Query("SELECT * FROM daily_app_usage WHERE bucketStartUtc >= :fromUtc AND bucketStartUtc < :toUtc")
    suspend fun between(fromUtc: Long, toUtc: Long): List<DailyAppUsageEntity>
}

@Dao
interface FeatureVectorDao {
    /** REPLACE: recomputing the same (labelDate, specVersion) supersedes the cached row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vector: FeatureVectorEntity)

    @Query("SELECT * FROM feature_vector WHERE labelDate = :labelDate AND specVersion = :specVersion")
    suspend fun find(labelDate: String, specVersion: String): FeatureVectorEntity?

    @Query("SELECT * FROM feature_vector WHERE specVersion = :specVersion ORDER BY labelDate DESC")
    suspend fun allForSpecVersion(specVersion: String): List<FeatureVectorEntity>

    /**
     * Retire vectors computed under a different SPEC_VERSION. Their feature definitions no
     * longer match the model's, so serving them would be exactly the silent drift the
     * version field exists to prevent.
     */
    @Query("DELETE FROM feature_vector WHERE specVersion != :specVersion")
    suspend fun deleteOtherSpecVersions(specVersion: String): Int
}

@Dao
interface ExtractionRunDao {
    @Insert
    suspend fun insert(run: ExtractionRunEntity): Long

    @Query("SELECT * FROM extraction_run ORDER BY ranAtUtc DESC LIMIT 1")
    suspend fun latest(): ExtractionRunEntity?

    @Query("SELECT * FROM extraction_run ORDER BY ranAtUtc DESC")
    suspend fun all(): List<ExtractionRunEntity>
}
