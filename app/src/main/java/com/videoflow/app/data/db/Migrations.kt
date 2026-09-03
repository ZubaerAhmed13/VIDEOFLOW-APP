package com.videoflow.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `project_settings` (
                `projectId` TEXT NOT NULL,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `frameRateNumerator` INTEGER NOT NULL,
                `frameRateDenominator` INTEGER NOT NULL,
                `backgroundArgb` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`projectId`),
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tracks` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `muted` INTEGER NOT NULL,
                `solo` INTEGER NOT NULL,
                `locked` INTEGER NOT NULL,
                `visible` INTEGER NOT NULL,
                `gainDb` REAL NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_projectId` ON `tracks` (`projectId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_projectId_orderIndex` ON `tracks` (`projectId`, `orderIndex`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `clips` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `trackId` TEXT NOT NULL,
                `assetId` TEXT NOT NULL,
                `timelineStartUs` INTEGER NOT NULL,
                `sourceStartUs` INTEGER NOT NULL,
                `sourceEndUs` INTEGER NOT NULL,
                `speed` REAL NOT NULL,
                `opacity` REAL NOT NULL,
                `enabled` INTEGER NOT NULL,
                `gainDb` REAL NOT NULL,
                `fadeInUs` INTEGER NOT NULL,
                `fadeOutUs` INTEGER NOT NULL,
                `x` REAL NOT NULL,
                `y` REAL NOT NULL,
                `scaleX` REAL NOT NULL,
                `scaleY` REAL NOT NULL,
                `rotationDegrees` REAL NOT NULL,
                `flipHorizontal` INTEGER NOT NULL,
                `flipVertical` INTEGER NOT NULL,
                `cropLeft` REAL NOT NULL,
                `cropTop` REAL NOT NULL,
                `cropRight` REAL NOT NULL,
                `cropBottom` REAL NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`assetId`) REFERENCES `media_assets`(`assetId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_clips_projectId` ON `clips` (`projectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_clips_trackId` ON `clips` (`trackId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_clips_assetId` ON `clips` (`assetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_clips_trackId_timelineStartUs` ON `clips` (`trackId`, `timelineStartUs`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `text_overlays` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `trackId` TEXT NOT NULL,
                `timelineStartUs` INTEGER NOT NULL,
                `timelineEndUs` INTEGER NOT NULL,
                `content` TEXT NOT NULL,
                `fontSizeSp` REAL NOT NULL,
                `fontWeight` INTEGER NOT NULL,
                `italic` INTEGER NOT NULL,
                `colorArgb` INTEGER NOT NULL,
                `opacity` REAL NOT NULL,
                `alignment` TEXT NOT NULL,
                `x` REAL NOT NULL,
                `y` REAL NOT NULL,
                `scaleX` REAL NOT NULL,
                `scaleY` REAL NOT NULL,
                `rotationDegrees` REAL NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_overlays_projectId` ON `text_overlays` (`projectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_overlays_trackId` ON `text_overlays` (`trackId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `image_overlays` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `trackId` TEXT NOT NULL,
                `assetId` TEXT NOT NULL,
                `timelineStartUs` INTEGER NOT NULL,
                `timelineEndUs` INTEGER NOT NULL,
                `x` REAL NOT NULL,
                `y` REAL NOT NULL,
                `scaleX` REAL NOT NULL,
                `scaleY` REAL NOT NULL,
                `rotationDegrees` REAL NOT NULL,
                `opacity` REAL NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`assetId`) REFERENCES `media_assets`(`assetId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_overlays_projectId` ON `image_overlays` (`projectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_overlays_trackId` ON `image_overlays` (`trackId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_overlays_assetId` ON `image_overlays` (`assetId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `keyframes` (
                `id` TEXT NOT NULL,
                `ownerId` TEXT NOT NULL,
                `ownerType` TEXT NOT NULL,
                `property` TEXT NOT NULL,
                `timeUs` INTEGER NOT NULL,
                `value` REAL NOT NULL,
                `interpolation` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_keyframes_ownerId` ON `keyframes` (`ownerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_keyframes_ownerId_property_timeUs` ON `keyframes` (`ownerId`, `property`, `timeUs`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `proxies` (
                `id` TEXT NOT NULL,
                `assetId` TEXT NOT NULL,
                `path` TEXT NOT NULL,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `codecMime` TEXT NOT NULL,
                `sourceFingerprint` TEXT,
                `status` TEXT NOT NULL,
                `quality` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `sizeBytes` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`assetId`) REFERENCES `media_assets`(`assetId`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_proxies_assetId` ON `proxies` (`assetId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `snapshots` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `projectFormatVersion` INTEGER NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_snapshots_projectId` ON `snapshots` (`projectId`)")

        db.execSQL("""
            INSERT OR IGNORE INTO project_settings(
                projectId, width, height, frameRateNumerator, frameRateDenominator,
                backgroundArgb, createdAt, updatedAt
            )
            SELECT id, 1920, 1080, 30, 1, -16777216, createdAt, updatedAt FROM projects
        """.trimIndent())
        db.execSQL("UPDATE projects SET projectFormatVersion = 2")
    }
}
