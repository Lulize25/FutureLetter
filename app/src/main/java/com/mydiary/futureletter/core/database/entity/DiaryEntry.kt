package com.mydiary.futureletter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日记实体。一个日期对应一篇日记（同日重复保存即更新）。
 * date 为该日期当地 0 点的 epoch millis。
 */
@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val contentMd: String,
    val date: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDraft: Boolean = false
)
