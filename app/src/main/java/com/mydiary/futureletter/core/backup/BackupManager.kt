package com.mydiary.futureletter.core.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.mydiary.futureletter.core.database.entity.DiaryEntry
import com.mydiary.futureletter.data.repository.DiaryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据备份与恢复。
 * - JSON：全量数据 + 图片 Base64 内嵌（单文件，适合文本传输）
 * - ZIP：entries.json + images/ 原图（适合长期存档）
 * 导入时按 ID 覆盖合并，图片写入应用私有目录。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diaryRepository: DiaryRepository
) {
    companion object {
        private const val BACKUP_VERSION = 1
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }

    private fun imageFiles(): List<File> {
        val dir = File(context.filesDir, "images")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.toList() ?: emptyList()
    }

    // ---------- 导出 ----------

    /** 生成备份 JSON 对象；embedImages = true 时图片以 Base64 内嵌 */
    private suspend fun buildBackupJson(embedImages: Boolean): JSONObject =
        withContext(Dispatchers.IO) {
            val entries = JSONArray()
            diaryRepository.getAllEntries().forEach { e ->
                entries.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("title", e.title)
                        .put("contentMd", e.contentMd)
                        .put("date", e.date)
                        .put("createdAt", e.createdAt)
                        .put("updatedAt", e.updatedAt)
                )
            }
            val images = JSONArray()
            imageFiles().forEach { f ->
                val obj = JSONObject().put("path", "images/${f.name}")
                if (embedImages) {
                    obj.put("base64", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                }
                images.put(obj)
            }
            JSONObject()
                .put("app", "com.mydiary.futureletter")
                .put("version", BACKUP_VERSION)
                .put("exportedAt", System.currentTimeMillis())
                .put("entries", entries)
                .put("images", images)
        }

    /** 导出为 JSON（图片 Base64 内嵌），写入用户选择的位置 */
    suspend fun exportJson(target: Uri): Int = withContext(Dispatchers.IO) {
        val json = buildBackupJson(embedImages = true)
        context.contentResolver.openOutputStream(target)?.use { out ->
            out.write(json.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("无法写入目标位置")
        json.getJSONArray("entries").length()
    }

    /** 导出为 ZIP（JSON 清单 + 图片原文件），写入用户选择的位置 */
    suspend fun exportZip(target: Uri): Int = withContext(Dispatchers.IO) {
        val json = buildBackupJson(embedImages = false)
        context.contentResolver.openOutputStream(target)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("entries.json"))
                zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                imageFiles().forEach { f ->
                    zip.putNextEntry(ZipEntry("images/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalStateException("无法写入目标位置")
        json.getJSONArray("entries").length()
    }

    // ---------- 导入 ----------

    /** 从 URI 恢复（自动识别 JSON 或 ZIP），返回导入的日记条数 */
    suspend fun importFrom(source: Uri): Int = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取所选文件")
        val isZip = bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        val json: JSONObject = if (isZip) readZip(bytes) else JSONObject(String(bytes, Charsets.UTF_8))
        restore(json)
    }

    private fun readZip(bytes: ByteArray): JSONObject {
        var manifest: JSONObject? = null
        val images = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val data = zip.readBytes()
                    when {
                        entry.name == "entries.json" -> manifest = JSONObject(String(data, Charsets.UTF_8))
                        entry.name.startsWith("images/") ->
                            images[File(entry.name).name] = data
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val m = manifest ?: throw IllegalStateException("备份文件缺少 entries.json")
        // 把 zip 内的图片以 Base64 挂到清单上，走统一恢复逻辑
        val arr = m.optJSONArray("images") ?: JSONArray()
        images.forEach { (name, data) ->
            arr.put(
                JSONObject()
                    .put("path", "images/$name")
                    .put("base64", Base64.encodeToString(data, Base64.NO_WRAP))
            )
        }
        m.put("images", arr)
        return m
    }

    private suspend fun restore(json: JSONObject): Int {
        val entries = json.optJSONArray("entries") ?: return 0

        // 先恢复图片
        val images = json.optJSONArray("images")
        if (images != null) {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            for (i in 0 until images.length()) {
                val obj = images.getJSONObject(i)
                val path = obj.optString("path") ?: continue
                val base64 = obj.optString("base64") ?: continue
                if (base64.isEmpty()) continue
                val name = File(path).name
                File(dir, name).writeBytes(Base64.decode(base64, Base64.NO_WRAP))
            }
        }

        // 再恢复日记（保留原 ID，覆盖同 ID 记录）
        var count = 0
        for (i in 0 until entries.length()) {
            val obj = entries.getJSONObject(i)
            val entry = DiaryEntry(
                id = obj.optLong("id", 0),
                title = obj.optString("title"),
                contentMd = obj.optString("contentMd"),
                date = obj.optLong("date"),
                createdAt = obj.optLong("createdAt"),
                updatedAt = obj.optLong("updatedAt")
            )
            diaryRepository.saveEntry(entry)
            count++
        }
        return count
    }
}
