package com.videoflow.app.di

import android.content.Context
import androidx.room.Room
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
            .build()
}
