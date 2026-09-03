package com.videoflow.app.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Keeps the legacy Step 1 project-creation path compatible while Step 2 is integrated.
 * The trigger is metadata-only: it never touches or copies user media.
 */
object Step2DatabaseCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS step2_project_defaults
            AFTER INSERT ON projects
            BEGIN
                UPDATE projects SET projectFormatVersion = 2 WHERE id = NEW.id;
                INSERT OR IGNORE INTO project_settings(
                    projectId, width, height, frameRateNumerator, frameRateDenominator,
                    backgroundArgb, createdAt, updatedAt
                ) VALUES(
                    NEW.id, 1920, 1080, 30, 1, -16777216, NEW.createdAt, NEW.updatedAt
                );
            END
        """.trimIndent())
    }
}
