package com.ahmetyuksel.merkez.core.database

import androidx.room.TypeConverter
import com.ahmetyuksel.merkez.core.model.EntryType
import com.ahmetyuksel.merkez.core.model.LinkRelation

class Converters {

    @TypeConverter
    fun entryTypeToString(value: EntryType): String = value.name

    @TypeConverter
    fun stringToEntryType(value: String): EntryType = EntryType.valueOf(value)

    @TypeConverter
    fun linkRelationToString(value: LinkRelation): String = value.name

    @TypeConverter
    fun stringToLinkRelation(value: String): LinkRelation = LinkRelation.valueOf(value)
}
