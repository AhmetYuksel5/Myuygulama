package com.ahmety.uygulama.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ahmety.uygulama.core.database.dao.ChangeLogDao
import com.ahmety.uygulama.core.database.dao.EntryDao
import com.ahmety.uygulama.core.database.dao.HabitDao
import com.ahmety.uygulama.core.database.dao.TagDao
import com.ahmety.uygulama.core.database.entity.ChangeLogEntity
import com.ahmety.uygulama.core.database.entity.EntryEntity
import com.ahmety.uygulama.core.database.entity.EntryFtsEntity
import com.ahmety.uygulama.core.database.entity.EntryLinkEntity
import com.ahmety.uygulama.core.database.entity.EntryTagCrossRef
import com.ahmety.uygulama.core.database.entity.HabitCheckEntity
import com.ahmety.uygulama.core.database.entity.HabitEntity
import com.ahmety.uygulama.core.database.entity.TagEntity

@Database(
    entities = [
        EntryEntity::class,
        EntryFtsEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class,
        EntryLinkEntity::class,
        HabitEntity::class,
        HabitCheckEntity::class,
        ChangeLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MerkezDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun tagDao(): TagDao
    abstract fun habitDao(): HabitDao
    abstract fun changeLogDao(): ChangeLogDao

    companion object {
        const val NAME = "merkez.db"
    }
}
