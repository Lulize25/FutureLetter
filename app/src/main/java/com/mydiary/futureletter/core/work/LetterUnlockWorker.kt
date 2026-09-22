package com.mydiary.futureletter.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.mydiary.futureletter.core.notification.NotificationHelper
import com.mydiary.futureletter.data.repository.LetterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 未来信解锁任务：到达设定时间后置为已解锁并发送本地通知。
 * 重启手机后 WorkManager 会自动恢复未执行的任务。
 */
@HiltWorker
class LetterUnlockWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val letterRepository: LetterRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val letterId = inputData.getLong(KEY_LETTER_ID, -1L)
        if (letterId < 0) return Result.failure()

        val letter = letterRepository.getLetter(letterId) ?: return Result.success()

        // 已解锁或不存在：无事可做
        if (letter.isUnlocked) return Result.success()

        val now = System.currentTimeMillis()
        if (letter.unlockAt > now) {
            // 系统提前唤醒（不太可能）：稍后重试
            return Result.retry()
        }

        letterRepository.markUnlocked(letterId)
        notificationHelper.showLetterUnlocked(letter.copy(isUnlocked = true))
        return Result.success()
    }

    companion object {
        const val KEY_LETTER_ID = "letterId"
    }
}

/** 未来信调度器：一封信对应一个唯一任务 */
object LetterScheduler {

    private fun workName(letterId: Long) = "letter_unlock_$letterId"

    fun schedule(context: Context, letter: com.mydiary.futureletter.core.database.entity.FutureLetter) {
        val delay = letter.unlockAt - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<LetterUnlockWorker>()
            .setInitialDelay(maxOf(delay, 0L), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(LetterUnlockWorker.KEY_LETTER_ID, letter.id).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(letter.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, letterId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(letterId))
    }
}
