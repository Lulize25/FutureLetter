package com.mydiary.futureletter.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mydiary.futureletter.core.database.entity.FutureLetter
import kotlinx.coroutines.flow.Flow

@Dao
interface LetterDao {

    @Upsert
    suspend fun upsertLetter(letter: FutureLetter): Long

    @Query("SELECT * FROM future_letters WHERE id = :id")
    suspend fun getLetter(id: Long): FutureLetter?

    @Query("SELECT * FROM future_letters WHERE id = :id")
    fun observeLetter(id: Long): Flow<FutureLetter?>

    @Query("DELETE FROM future_letters WHERE id = :id")
    suspend fun deleteLetter(id: Long)

    @Query("SELECT * FROM future_letters ORDER BY unlockAt ASC")
    fun observeAllLetters(): Flow<List<FutureLetter>>

    @Query("SELECT * FROM future_letters")
    suspend fun getAllLetters(): List<FutureLetter>

    /** 已到解锁时间但还未解锁的信（启动兜底扫描用） */
    @Query("SELECT * FROM future_letters WHERE unlockAt <= :now AND isUnlocked = 0")
    suspend fun getDueLetters(now: Long): List<FutureLetter>

    @Query("UPDATE future_letters SET isUnlocked = 1 WHERE id = :id")
    suspend fun markUnlocked(id: Long)
}
