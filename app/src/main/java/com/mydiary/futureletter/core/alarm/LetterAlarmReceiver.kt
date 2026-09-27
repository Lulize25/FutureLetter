package com.mydiary.futureletter.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mydiary.futureletter.data.repository.LetterRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 未来信精确闹钟接收器。
 * - 收到 UNLOCK_LETTER：解锁对应信件并发通知
 * - 收到 BOOT_COMPLETED：开机后重建所有未解锁信的闹钟（闹钟不随重启保留）
 */
@AndroidEntryPoint
class LetterAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var letterRepository: LetterRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_UNLOCK_LETTER -> {
                val letterId = intent.getLongExtra(EXTRA_LETTER_ID, -1L)
                if (letterId < 0) return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val letter = letterRepository.getLetter(letterId)
                        if (letter != null && !letter.isUnlocked) {
                            val now = System.currentTimeMillis()
                            if (letter.unlockAt > now) {
                                // 系统提前唤醒：重新排定
                                LetterAlarmScheduler.schedule(context, letter)
                            } else {
                                letterRepository.markUnlocked(letter.id)
                                letterRepository.notifyUnlocked(letter)
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        letterRepository.rescheduleAllPending()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_UNLOCK_LETTER = "com.mydiary.futureletter.UNLOCK_LETTER"
        const val EXTRA_LETTER_ID = "letterId"

        fun buildUnlockIntent(context: Context, letterId: Long): Intent =
            Intent(context, LetterAlarmReceiver::class.java).apply {
                action = ACTION_UNLOCK_LETTER
                putExtra(EXTRA_LETTER_ID, letterId)
            }
    }
}

/** 精确闹钟调度：优先用 AlarmManager 精确触发，权限缺失时退回 WorkManager */
object LetterAlarmScheduler {

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return am?.canScheduleExactAlarms() == true
    }

    fun schedule(context: Context, letter: com.mydiary.futureletter.core.database.entity.FutureLetter) {
        // 先清掉旧闹钟
        cancel(context, letter.id)
        if (letter.isUnlocked) return

        if (canScheduleExact(context)) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                letter.id.toInt(),
                LetterAlarmReceiver.buildUnlockIntent(context, letter.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                letter.unlockAt,
                pendingIntent
            )
        }
        // WorkManager 兜底（闹钟被系统取消或权限缺失时仍有机会触发；
        // 两者同时存在时后到的一方会发现已解锁，自动空转）
        com.mydiary.futureletter.core.work.LetterScheduler.schedule(context, letter)
    }

    fun cancel(context: Context, letterId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            letterId.toInt(),
            LetterAlarmReceiver.buildUnlockIntent(context, letterId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am?.cancel(pendingIntent)
        com.mydiary.futureletter.core.work.LetterScheduler.cancel(context, letterId)
    }
}
