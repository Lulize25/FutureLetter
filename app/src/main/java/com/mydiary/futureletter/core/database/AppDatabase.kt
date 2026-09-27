package com.mydiary.futureletter.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mydiary.futureletter.core.database.dao.DiaryDao
import com.mydiary.futureletter.core.database.entity.DiaryEntry
import com.mydiary.futureletter.core.database.entity.DiaryEntryFts
import com.mydiary.futureletter.core.database.entity.DiaryTagCrossRef
import com.mydiary.futureletter.core.database.entity.Tag

@Database(
    entities = [
        DiaryEntry::class,
        Tag::class,
        DiaryTagCrossRef::class,
        DiaryEntryFts::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "future_letter.db"
                ).build().also { instance = it }
            }
    }
}
