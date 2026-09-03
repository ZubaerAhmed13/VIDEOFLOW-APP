package com.videoflow.app.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.data.db.MIGRATION_1_2
import com.videoflow.app.data.db.VideoFlowDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step2MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VideoFlowDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration1To2PreservesStep1FieldsAndCreatesEditorSchema() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO projects(id,name,projectFormatVersion,createdAt,updatedAt,lastOpenedAt) VALUES('p','Legacy',1,10,20,20)")
            execSQL("""
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
            """.trimIndent())
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT name, projectFormatVersion, createdAt, updatedAt FROM projects WHERE id='p'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Legacy", cursor.getString(0))
                assertEquals(2, cursor.getInt(1))
                assertEquals(10L, cursor.getLong(2))
                assertEquals(20L, cursor.getLong(3))
            }
            db.query("SELECT sourceUri, sizeBytes, fingerprintSha256 FROM media_assets WHERE assetId='a'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("content://legacy/video", cursor.getString(0))
                assertEquals(4_294_967_296L, cursor.getLong(1))
                assertEquals("abc", cursor.getString(2))
            }
            db.query("SELECT width,height,frameRateNumerator,frameRateDenominator FROM project_settings WHERE projectId='p'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1920, cursor.getInt(0))
                assertEquals(1080, cursor.getInt(1))
                assertEquals(30, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
            }
        }
    }

    companion object {
        private const val DB_NAME = "step2-migration-test"
    }
}
