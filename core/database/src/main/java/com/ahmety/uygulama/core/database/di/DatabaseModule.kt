package com.ahmety.uygulama.core.database.di

import android.content.Context
import androidx.room.Room
import com.ahmety.uygulama.core.database.MerkezDatabase
import com.ahmety.uygulama.core.database.dao.ChangeLogDao
import com.ahmety.uygulama.core.database.dao.EntryDao
import com.ahmety.uygulama.core.database.dao.HabitDao
import com.ahmety.uygulama.core.database.dao.TagDao
import com.ahmety.uygulama.core.database.dao.SyncStateDao
import com.ahmety.uygulama.core.database.dao.TaskDao
import com.ahmety.uygulama.core.database.sync.DeviceId
import com.ahmety.uygulama.core.database.sync.Now
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val PREFS_NAME = "merkez_cihaz"
    private const val KEY_DEVICE_ID = "device_id"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MerkezDatabase =
        Room.databaseBuilder(context, MerkezDatabase::class.java, MerkezDatabase.NAME)
            .build()

    @Provides
    fun provideEntryDao(database: MerkezDatabase): EntryDao = database.entryDao()

    @Provides
    fun provideTagDao(database: MerkezDatabase): TagDao = database.tagDao()

    @Provides
    fun provideHabitDao(database: MerkezDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideTaskDao(database: MerkezDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideChangeLogDao(database: MerkezDatabase): ChangeLogDao = database.changeLogDao()

    /**
     * Cihazın kalıcı kimliği. Değişiklik günlüğünde "bu satırı hangi telefon üretti"
     * bilgisini taşır; ilk çalıştırmada üretilir ve bir daha değişmez.
     */
    @Provides
    @Singleton
    @DeviceId
    fun provideDeviceId(@ApplicationContext context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    @Provides
    fun provideSyncStateDao(database: MerkezDatabase): SyncStateDao = database.syncStateDao()

    @Provides
    @Singleton
    fun provideNow(): Now = Now { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
