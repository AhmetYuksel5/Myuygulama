package com.ahmetyuksel.merkez.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ahmetyuksel.merkez.core.database.dao.EntryDao
import com.ahmetyuksel.merkez.core.database.dao.TagDao
import com.ahmetyuksel.merkez.core.database.entity.EntryEntity
import com.ahmetyuksel.merkez.core.database.entity.EntryFtsEntity
import com.ahmetyuksel.merkez.core.database.entity.EntryLinkEntity
import com.ahmetyuksel.merkez.core.database.entity.EntryTagCrossRef
import com.ahmetyuksel.merkez.core.database.entity.TagEntity

@Database(
    entities = [
        EntryEntity::class,
        EntryFtsEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class,
        EntryLinkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MerkezDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "merkez.db"
    }
}
