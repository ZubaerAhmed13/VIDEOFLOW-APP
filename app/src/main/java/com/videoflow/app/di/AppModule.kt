package com.videoflow.app.di

import android.content.Context
import androidx.room.Room
import com.videoflow.app.data.db.MIGRATION_1_2
import com.videoflow.app.data.db.Step2DatabaseCallback
import com.videoflow.app.data.db.VideoFlowDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VideoFlowDatabase =
        Room.databaseBuilder(context, VideoFlowDatabase::class.java, "videoflow.db")
            .addMigrations(MIGRATION_1_2)
            .addCallback(Step2DatabaseCallback)
            .build()
}
