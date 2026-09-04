package com.videoflow.app.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.videoflow.app.data.db.MIGRATION_1_2
import com.videoflow.app.data.db.MIGRATION_2_3
import com.videoflow.app.data.db.Step2DatabaseCallback
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.render.Media3RenderEngine
import com.videoflow.app.render.RenderEngine
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(Step2DatabaseCallback)
            .build()

    @Provides
    @Singleton
    @UnstableApi
    fun provideRenderEngine(engine: Media3RenderEngine): RenderEngine = engine
}
