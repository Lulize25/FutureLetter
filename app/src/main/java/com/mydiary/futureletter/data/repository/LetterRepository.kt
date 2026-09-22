package com.mydiary.futureletter.data.repository

import android.content.Context
import com.mydiary.futureletter.core.database.dao.LetterDao
import com.mydiary.futureletter.core.database.entity.FutureLetter
import com.mydiary.futureletter.core.notification.NotificationHelper
import com.mydiary.futureletter.core.work.LetterScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LetterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val letterDao: LetterDao,
    private val notificationHelper: NotificationHelper
) {
    fun observeAllLetters(): Flow<List<FutureLetter>> = letterDao.observeAllLetters()

    suspend fun getLetter(id: Long): FutureLetter? = letterDao.getLetter(id)

    suspend fun getAllLetters(): List<FutureLetter> = letterDao.getAllLetters()

    /** 保存并调度解锁任务 */
    suspend fun saveLetter(letter: FutureLetter): Long {
        val id = letterDao.upsertLetter(letter)
        val saved = letterDao.getLetter(id) ?: return id
        if (!saved.isUnlocked && saved.unlockAt > System.currentTimeMillis()) {
            LetterScheduler.schedule(context, saved)
        }
        return id
    }

    /** 删除并取消对应的调度任务 */
    suspend fun deleteLetter(id: Long) {
        LetterScheduler.cancel(context, id)
        letterDao.deleteLetter(id)
    }

    suspend fun markUnlocked(id: Long) = letterDao.markUnlocked(id)

    /**
     * 启动兜底扫描：已到时间但还没解锁的信，补解锁 + 补发通知。
     * WorkManager 重启后任务仍在，但系统可能推迟执行，这里保证最终一致。
     */
    suspend fun sweepDueLetters(): Int {
        val now = System.currentTimeMillis()
        val due = letterDao.getDueLetters(now)
        due.forEach { letter ->
            letterDao.markUnlocked(letter.id)
            notificationHelper.showLetterUnlocked(letter)
        }
        return due.size
    }
}
