package com.mydiary.futureletter.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 日记-标签 多对多关联表。
 */
@Entity(
    tableName = "diary_tag_cross_refs",
    primaryKeys = ["diaryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntry::class,
            parentColumns = ["id"],
            childColumns = ["diaryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class DiaryTagCrossRef(
    val diaryId: Long,
    val tagId: Long
)
