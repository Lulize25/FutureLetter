package com.mydiary.futureletter.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mydiary.futureletter.core.database.dao.DiaryDao
import com.mydiary.futureletter.core.database.dao.LetterDao
import com.mydiary.futureletter.core.database.entity.DiaryEntry
import com.mydiary.futureletter.core.database.entity.DiaryEntryFts
import com.mydiary.futureletter.core.database.entity.DiaryTagCrossRef
import com.mydiary.futureletter.core.database.entity.FutureLetter
import com.mydiary.futureletter.core.database.entity.Tag

@Database(
    entities = [
        DiaryEntry::class,
        Tag::class,
        DiaryTagCrossRef::class,
        DiaryEntryFts::class,
        FutureLetter::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun letterDao(): LetterDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** 1 → 2：新增 future_letters 表（保留已有日记数据） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `future_letters` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `contentMd` TEXT NOT NULL,
                        `unlockAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `isUnlocked` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "future_letter.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
