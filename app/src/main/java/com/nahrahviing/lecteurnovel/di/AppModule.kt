package com.nahrahviing.lecteurnovel.di

import android.content.Context
import com.nahrahviing.lecteurnovel.data.local.AppDatabase
import com.nahrahviing.lecteurnovel.data.local.NovelDao
import com.nahrahviing.lecteurnovel.data.repository.NovelRepository
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideNovelDao(database: AppDatabase): NovelDao {
        return database.novelDao()
    }

    @Provides
    @Singleton
    fun provideNovelRepository(@ApplicationContext context: Context): NovelRepository {
        return NovelRepository(context)
    }
}
