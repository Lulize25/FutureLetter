package com.mydiary.futureletter.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * 日记全文检索虚拟表（FTS4），Room 会自动维护与 diary_entries 的同步触发器。
 */
@Fts4(contentEntity = DiaryEntry::class)
@Entity(tableName = "diary_entry_fts")
data class DiaryEntryFts(
    val title: String,
    val contentMd: String
)
