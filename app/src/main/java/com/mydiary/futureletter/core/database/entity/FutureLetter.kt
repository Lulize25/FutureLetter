package com.mydiary.futureletter.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 未来信实体。解锁前列表只显示标题 + 倒计时，正文在 UI 层隐藏（数据库本身不加密）。
 */
@Entity(tableName = "future_letters")
data class FutureLetter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val contentMd: String,
    val unlockAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isUnlocked: Boolean = false
)
