package com.ahmety.uygulama.core.database.di

import android.content.Context
import androidx.room.Room
import com.ahmety.uygulama.core.database.MerkezDatabase
import com.ahmety.uygulama.core.database.dao.EntryDao
import com.ahmety.uygulama.core.database.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MerkezDatabase =
        Room.databaseBuilder(context, MerkezDatabase::class.java, MerkezDatabase.NAME)
            .build()

    @Provides
    fun provideEntryDao(database: MerkezDatabase): EntryDao = database.entryDao()

    @Provides
    fun provideTagDao(database: MerkezDatabase): TagDao = database.tagDao()
}
