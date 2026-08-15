package com.stressdetect.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * On-device store. Never synced, never uploaded, excluded from cloud backup and
 * device-to-device transfer (`res/xml/data_extraction_rules.xml`).
 */
@Database(
    entities = [
        RawUsageEventEntity::class,
        LockedIntervalEntity::class,
        CommEventEntity::class,
        DailyAppUsageEntity::class,
        FeatureVectorEntity::class,
        ExtractionRunEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class StressDetectDatabase : RoomDatabase() {

    abstract fun rawUsageEventDao(): RawUsageEventDao
    abstract fun lockedIntervalDao(): LockedIntervalDao
    abstract fun commEventDao(): CommEventDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun featureVectorDao(): FeatureVectorDao
    abstract fun extractionRunDao(): ExtractionRunDao

    companion object {
        private const val NAME = "stress_detect.db"

        @Volatile
        private var instance: StressDetectDatabase? = null

        fun get(context: Context): StressDetectDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, StressDetectDatabase::class.java, NAME)
                    // No fallbackToDestructiveMigration: the raw event history cannot be
                    // re-queried (~10-day OS retention), so dropping it on a schema change
                    // would permanently destroy a participant's baseline. Write migrations.
                    .build()
                    .also { instance = it }
            }
    }
}
