package au.com.firstclassexpress.driver.data.local

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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `job_timeline_events` (
                `id` TEXT NOT NULL,
                `jobId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `timestamp` INTEGER NOT NULL,
                `latitude` REAL,
                `longitude` REAL,
                `syncStatus` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_timeline_events_jobId` ON `job_timeline_events` (`jobId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_timeline_events_timestamp` ON `job_timeline_events` (`timestamp`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `driver_incidents` (
                `id` TEXT NOT NULL,
                `driverId` TEXT NOT NULL,
                `shiftId` TEXT,
                `jobId` TEXT,
                `category` TEXT NOT NULL,
                `severity` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `photoUri` TEXT,
                `latitude` REAL,
                `longitude` REAL,
                `createdAt` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_driver_incidents_driverId` ON `driver_incidents` (`driverId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_driver_incidents_jobId` ON `driver_incidents` (`jobId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_driver_incidents_createdAt` ON `driver_incidents` (`createdAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `driver_messages` (
                `id` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `jobId` TEXT,
                `timestamp` INTEGER NOT NULL,
                `isRead` INTEGER NOT NULL,
                `urgency` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_driver_messages_category` ON `driver_messages` (`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_driver_messages_timestamp` ON `driver_messages` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_driver_messages_isRead` ON `driver_messages` (`isRead`)")
    }
}

/**
 * Phase 3 Live Operations & Telemetry growth:
 * - Telemetry enhancements on `location_points` (vehicleId, batteryLevel, networkState, source)
 * - Revision tracking on `jobs` (revision, serverUpdatedAt)
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `location_points` ADD COLUMN `vehicleId` TEXT")
        db.execSQL("ALTER TABLE `location_points` ADD COLUMN `batteryLevel` INTEGER")
        db.execSQL("ALTER TABLE `location_points` ADD COLUMN `networkState` TEXT")
        db.execSQL("ALTER TABLE `location_points` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'FUSED_LOCATION'")

        db.execSQL("ALTER TABLE `jobs` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `jobs` ADD COLUMN `serverUpdatedAt` INTEGER")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
