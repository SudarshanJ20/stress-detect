package com.stressdetect.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        CheckInEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class StressDetectDatabase : RoomDatabase() {

    abstract fun rawUsageEventDao(): RawUsageEventDao
    abstract fun lockedIntervalDao(): LockedIntervalDao
    abstract fun commEventDao(): CommEventDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun featureVectorDao(): FeatureVectorDao
    abstract fun extractionRunDao(): ExtractionRunDao
    abstract fun checkInDao(): CheckInDao

    companion object {
        private const val NAME = "stress_detect.db"

        /**
         * v1 → v2: adds the check-in history table.
         *
         * Written by hand rather than destructively recreated: the raw event history in the
         * other tables cannot be re-queried (the OS prunes it after ~10 days), so dropping
         * the database on a schema change would permanently destroy a participant's
         * baseline. That is also why there is no `fallbackToDestructiveMigration` below.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `check_in` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `takenAtUtc` INTEGER NOT NULL,
                        `score` INTEGER NOT NULL,
                        `isDemo` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_check_in_takenAtUtc` " +
                        "ON `check_in` (`takenAtUtc`)"
                )
            }
        }

        @Volatile
        private var instance: StressDetectDatabase? = null

        fun get(context: Context): StressDetectDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, StressDetectDatabase::class.java, NAME)
                    .addMigrations(MIGRATION_1_2)
                    // No fallbackToDestructiveMigration: the raw event history cannot be
                    // re-queried (~10-day OS retention), so dropping it on a schema change
                    // would permanently destroy a participant's baseline. Write migrations.
                    .build()
                    .also { instance = it }
            }
    }
}
