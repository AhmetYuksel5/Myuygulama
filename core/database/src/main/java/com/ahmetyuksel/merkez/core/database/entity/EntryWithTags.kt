package com.ahmetyuksel.merkez.core.database.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class EntryWithTags(
    @Embedded val entry: EntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryTagCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)
