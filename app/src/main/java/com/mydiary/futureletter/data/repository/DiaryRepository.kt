package com.mydiary.futureletter.data.repository

import com.mydiary.futureletter.core.database.dao.DiaryDao
import com.mydiary.futureletter.core.database.entity.DiaryEntry
import com.mydiary.futureletter.core.database.entity.Tag
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    private val diaryDao: DiaryDao
) {
    fun observeEntry(id: Long): Flow<DiaryEntry?> = diaryDao.observeEntry(id)

    fun observeEntryByDate(date: Long): Flow<DiaryEntry?> = diaryDao.observeEntryByDate(date)

    suspend fun getEntry(id: Long): DiaryEntry? = diaryDao.getEntry(id)

    suspend fun getEntryByDate(date: Long): DiaryEntry? = diaryDao.getEntryByDate(date)

    suspend fun saveEntry(entry: DiaryEntry): Long = diaryDao.upsertEntry(entry)

    suspend fun deleteEntry(id: Long) = diaryDao.deleteEntry(id)

    fun observeDatesInRange(start: Long, end: Long): Flow<List<Long>> =
        diaryDao.observeDatesInRange(start, end)

    /** FTS 查询：转义用户输入中的特殊字符，多个关键词用空格分隔 */
    fun searchEntries(rawQuery: String): Flow<List<DiaryEntry>> =
        diaryDao.searchEntries(toFtsQuery(rawQuery))

    fun observeAllEntries(): Flow<List<DiaryEntry>> = diaryDao.observeAllEntries()

    suspend fun getAllEntries(): List<DiaryEntry> = diaryDao.getAllEntries()

    fun observeAllTags(): Flow<List<Tag>> = diaryDao.observeAllTags()

    suspend fun getAllTags(): List<Tag> = diaryDao.getAllTags()

    suspend fun getOrCreateTag(name: String): Long = diaryDao.getOrCreateTag(name.trim())

    fun observeTagsForDiary(diaryId: Long): Flow<List<Tag>> =
        diaryDao.observeTagsForDiary(diaryId)

    suspend fun getTagsForDiary(diaryId: Long): List<Tag> = diaryDao.getTagsForDiary(diaryId)

    suspend fun setTagsForDiary(diaryId: Long, tagNames: List<String>) {
        val ids = tagNames.filter { it.isNotBlank() }.map { diaryDao.getOrCreateTag(it.trim()) }
        diaryDao.setTagsForDiary(diaryId, ids)
    }

    private fun toFtsQuery(raw: String): String =
        raw.trim()
            .replace("\"", "\"\"")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }
}
