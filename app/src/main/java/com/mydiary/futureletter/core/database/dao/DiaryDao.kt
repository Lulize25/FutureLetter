package com.mydiary.futureletter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.mydiary.futureletter.core.database.entity.DiaryEntry
import com.mydiary.futureletter.core.database.entity.DiaryTagCrossRef
import com.mydiary.futureletter.core.database.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Upsert
    suspend fun upsertEntry(entry: DiaryEntry): Long

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    fun observeEntry(id: Long): Flow<DiaryEntry?>

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun getEntry(id: Long): DiaryEntry?

    @Query("SELECT * FROM diary_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryByDate(date: Long): DiaryEntry?

    @Query("SELECT * FROM diary_entries WHERE date = :date LIMIT 1")
    fun observeEntryByDate(date: Long): Flow<DiaryEntry?>

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    /** 某月范围内有日记的日期（毫秒） */
    @Query("SELECT date FROM diary_entries WHERE date >= :start AND date < :end")
    fun observeDatesInRange(start: Long, end: Long): Flow<List<Long>>

    /** 全文搜索：标题 + 正文 */
    @Query(
        """
        SELECT e.* FROM diary_entries e
        JOIN diary_entry_fts fts ON e.id = fts.rowid
        WHERE diary_entry_fts MATCH :query
        ORDER BY e.date DESC
        """
    )
    fun searchEntries(query: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries ORDER BY date DESC")
    fun observeAllEntries(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries")
    suspend fun getAllEntries(): List<DiaryEntry>

    // ---------- 标签 ----------

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun getAllTags(): List<Tag>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagIgnore(tag: Tag): Long

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): Tag?

    /** 不存在则创建，返回标签 id */
    suspend fun getOrCreateTag(name: String): Long {
        findTagByName(name)?.let { return it.id }
        return insertTagIgnore(Tag(name = name))
    }

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN diary_tag_cross_refs r ON t.id = r.tagId
        WHERE r.diaryId = :diaryId
        """
    )
    fun observeTagsForDiary(diaryId: Long): Flow<List<Tag>>

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN diary_tag_cross_refs r ON t.id = r.tagId
        WHERE r.diaryId = :diaryId
        """
    )
    suspend fun getTagsForDiary(diaryId: Long): List<Tag>

    @Query("DELETE FROM diary_tag_cross_refs WHERE diaryId = :diaryId")
    suspend fun clearTagsForDiary(diaryId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<DiaryTagCrossRef>)

    /** 覆盖式设置一篇日记的标签 */
    suspend fun setTagsForDiary(diaryId: Long, tagIds: List<Long>) {
        clearTagsForDiary(diaryId)
        if (tagIds.isNotEmpty()) {
            insertCrossRefs(tagIds.map { DiaryTagCrossRef(diaryId, it) })
        }
    }
}
