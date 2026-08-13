package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 2A schema growth. Every statement is additive — no table is dropped or recreated, so
 * existing jobs, inspections, evidence and queued sync operations survive the upgrade.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `driver_credentials` (
                `driverId` TEXT NOT NULL,
                `loginId` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `phone` TEXT,
                `pinSalt` TEXT NOT NULL,
                `pinHash` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`driverId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_driver_credentials_loginId` " +
                "ON `driver_credentials` (`loginId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_driver_credentials_email` " +
                "ON `driver_credentials` (`email`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `freight_exceptions` (
                `id` TEXT NOT NULL,
                `jobId` TEXT NOT NULL,
                `stage` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `driverId` TEXT NOT NULL,
                `shiftId` TEXT,
                `resolved` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_freight_exceptions_jobId` " +
                "ON `freight_exceptions` (`jobId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_freight_exceptions_status` " +
                "ON `freight_exceptions` (`status`)"
        )

        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `driverId` TEXT")
        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `shiftId` TEXT")
        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `signerName` TEXT")
        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `notes` TEXT")
        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `fileSizeBytes` INTEGER")
        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `savedAt` INTEGER")

        db.execSQL("ALTER TABLE `drivers` ADD COLUMN `phone` TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `location_points` (
                `id` TEXT NOT NULL,
                `driverId` TEXT NOT NULL,
                `shiftId` TEXT NOT NULL,
                `jobId` TEXT,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `accuracyMeters` REAL NOT NULL,
                `speedMetersPerSecond` REAL,
                `bearingDegrees` REAL,
                `altitudeMeters` REAL,
                `recordedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_points_driverId` ON `location_points` (`driverId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_points_shiftId` ON `location_points` (`shiftId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_points_jobId` ON `location_points` (`jobId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_points_recordedAt` ON `location_points` (`recordedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_points_syncStatus` ON `location_points` (`syncStatus`)")
        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `latitude` REAL")
        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `longitude` REAL")
        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `locationAccuracyMeters` REAL")
        db.execSQL("ALTER TABLE `evidence` ADD COLUMN `locationRecordedAt` INTEGER")
    }
}

/**
 * Phase 2B2 sync engine. Two additive columns on the existing queue — no table is rebuilt, so
 * operations already waiting to sync survive the upgrade and keep their idempotency keys.
 *
 * `payloadVersion` defaults to 1 so rows written by an older app version are understood by the
 * new processor. `updatedAt` defaults to 0, which makes any pre-existing row immediately eligible
 * for stale-lease recovery — correct, because nothing was ever mid-flight before this release.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `sync_operations` ADD COLUMN `payloadVersion` INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "ALTER TABLE `sync_operations` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
