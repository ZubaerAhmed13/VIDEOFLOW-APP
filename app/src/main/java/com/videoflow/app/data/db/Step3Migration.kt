package com.videoflow.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Step 3 adds export job/history persistence only. Project semantic format remains v2.
 * Existing projects, media, editor state, proxies and snapshots are not rewritten.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `export_jobs` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `destinationUri` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `settingsJson` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `progress` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `failureCode` TEXT,
                `failureMessage` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_export_jobs_projectId` ON `export_jobs` (`projectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_export_jobs_status` ON `export_jobs` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_export_jobs_createdAt` ON `export_jobs` (`createdAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `export_reports` (
                `id` TEXT NOT NULL,
                `jobId` TEXT NOT NULL,
                `outputWidth` INTEGER NOT NULL,
                `outputHeight` INTEGER NOT NULL,
                `frameRateNumerator` INTEGER NOT NULL,
                `frameRateDenominator` INTEGER NOT NULL,
                `videoCodecMime` TEXT NOT NULL,
                `encoderName` TEXT,
                `videoBitrate` INTEGER NOT NULL,
                `audioCodecMime` TEXT,
                `audioBitrate` INTEGER,
                `colorStandard` INTEGER,
                `colorRange` INTEGER,
                `colorTransfer` INTEGER,
                `hdrPreserved` INTEGER NOT NULL,
                `durationUs` INTEGER NOT NULL,
                `fileSizeBytes` INTEGER NOT NULL,
                `renderDurationMs` INTEGER NOT NULL,
                `validationPassed` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`jobId`) REFERENCES `export_jobs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_export_reports_jobId` ON `export_reports` (`jobId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_export_reports_createdAt` ON `export_reports` (`createdAt`)")
    }
}
