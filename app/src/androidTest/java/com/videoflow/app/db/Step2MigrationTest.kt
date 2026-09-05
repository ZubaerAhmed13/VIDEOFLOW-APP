package com.videoflow.app.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.data.db.MIGRATION_1_2
import com.videoflow.app.data.db.MIGRATION_2_3
import com.videoflow.app.data.db.Step2DatabaseCallback
import com.videoflow.app.data.db.VideoFlowDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step2MigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migration1To3PreservesStep1AndStep2DataAndRoomValidatesCurrentSchema() {
        createAuthenticVersion1Database()

        val room = Room.databaseBuilder(context, VideoFlowDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(Step2DatabaseCallback)
            .allowMainThreadQueries()
            .build()

        try {
            // Opening the real production database executes the complete 1 -> 2 -> 3 chain and
            // then Room's generated v3 schema validator. Any missing column, FK, index, affinity
            // or nullability mismatch fails this open.
            val db = room.openHelper.writableDatabase
            assertEquals(3, db.version)

            db.query("SELECT name, projectFormatVersion, createdAt, updatedAt FROM projects WHERE id='p'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy", cursor.getString(0))
                // Step 3 adds export persistence only; the editor project semantic format remains v2.
                assertEquals(2, cursor.getInt(1))
                assertEquals(10L, cursor.getLong(2))
                assertEquals(20L, cursor.getLong(3))
            }

            db.query("SELECT sourceUri, sizeBytes, fingerprintSha256 FROM media_assets WHERE assetId='a'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("content://legacy/video", cursor.getString(0))
                assertEquals(4_294_967_296L, cursor.getLong(1))
                assertEquals("abc", cursor.getString(2))
            }

            db.query("SELECT width,height,frameRateNumerator,frameRateDenominator,backgroundArgb FROM project_settings WHERE projectId='p'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1920, cursor.getInt(0))
                assertEquals(1080, cursor.getInt(1))
                assertEquals(30, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(-16_777_216, cursor.getInt(4))
            }

            EXPECTED_CURRENT_TABLES.forEach { table ->
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { cursor ->
                    assertTrue("Missing migrated table: $table", cursor.moveToFirst())
                    assertEquals(table, cursor.getString(0))
                }
            }

            db.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name='step2_project_defaults'").use { cursor ->
                assertTrue("Production Step 2 project-default trigger was not installed", cursor.moveToFirst())
            }

            db.query("SELECT identity_hash FROM room_master_table WHERE id=42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                val migratedIdentity = cursor.getString(0)
                assertTrue(migratedIdentity.isNotBlank())
                assertTrue("Room identity was not advanced from the v1 schema", migratedIdentity != V1_IDENTITY_HASH)
            }
        } finally {
            room.close()
        }
    }

    /** Builds the checked-in Step 1 Room v1 schema and representative persisted data. */
    private fun createAuthenticVersion1Database() {
        val dbFile = context.getDatabasePath(DB_NAME)
        dbFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.beginTransaction()
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `projects` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `projectFormatVersion` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastOpenedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_assets` (
                        `assetId` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `sourceUri` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `mimeType` TEXT,
                        `sizeBytes` INTEGER,
                        `durationUs` INTEGER,
                        `width` INTEGER,
                        `height` INTEGER,
                        `rotationDegrees` INTEGER,
                        `frameRate` REAL,
                        `videoCodecMime` TEXT,
                        `audioCodecMime` TEXT,
                        `audioSampleRate` INTEGER,
                        `audioChannelCount` INTEGER,
                        `videoTrackCount` INTEGER NOT NULL,
                        `audioTrackCount` INTEGER NOT NULL,
                        `videoBitrate` INTEGER,
                        `videoProfile` INTEGER,
                        `videoLevel` INTEGER,
                        `colorStandard` INTEGER,
                        `colorTransfer` INTEGER,
                        `colorRange` INTEGER,
                        `hdrStaticInfoPresent` INTEGER NOT NULL,
                        `fingerprintSha256` TEXT,
                        `fingerprintAlgorithm` TEXT,
                        `fingerprintStrength` TEXT NOT NULL,
                        `fingerprintSampledBytes` INTEGER NOT NULL,
                        `fingerprintNote` TEXT,
                        `permissionPersisted` INTEGER NOT NULL,
                        `sourceStatus` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`assetId`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_assets_projectId` ON `media_assets` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_assets_projectId_sourceUri` ON `media_assets` (`projectId`, `sourceUri`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V1_IDENTITY_HASH')")

                db.execSQL("INSERT INTO projects(id,name,projectFormatVersion,createdAt,updatedAt,lastOpenedAt) VALUES('p','Legacy',1,10,20,20)")
                db.execSQL(
                    """
                    INSERT INTO media_assets(
                        assetId, projectId, sourceUri, displayName, mimeType, sizeBytes, durationUs,
                        width, height, rotationDegrees, frameRate, videoCodecMime, audioCodecMime,
                        audioSampleRate, audioChannelCount, videoTrackCount, audioTrackCount, videoBitrate,
                        videoProfile, videoLevel, colorStandard, colorTransfer, colorRange, hdrStaticInfoPresent,
                        fingerprintSha256, fingerprintAlgorithm, fingerprintStrength, fingerprintSampledBytes,
                        fingerprintNote, permissionPersisted, sourceStatus, createdAt
                    ) VALUES(
                        'a','p','content://legacy/video','legacy.mp4','video/mp4',4294967296,60000000,
                        3840,2160,0,29.97,'video/hevc','audio/mp4a-latm',48000,2,1,1,50000000,
                        NULL,NULL,NULL,NULL,NULL,0,'abc','VideoFlowSampleSHA256-v1','STRONG_THREE_REGION',12582912,
                        NULL,1,'AVAILABLE',10
                    )
                    """.trimIndent()
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.version = 1
        }
    }

    companion object {
        private const val DB_NAME = "step2-migration-test"
        private const val V1_IDENTITY_HASH = "2f10f3828cd9e06d6e20d77a6df6bd65"
        private val EXPECTED_CURRENT_TABLES = listOf(
            "project_settings",
            "tracks",
            "clips",
            "text_overlays",
            "image_overlays",
            "keyframes",
            "proxies",
            "snapshots",
            "export_jobs",
            "export_reports"
        )
    }
}
