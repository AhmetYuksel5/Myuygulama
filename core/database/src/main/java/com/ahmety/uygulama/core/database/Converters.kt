package com.ahmety.uygulama.core.database

import androidx.room.TypeConverter
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.LinkRelation

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
