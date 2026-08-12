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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
