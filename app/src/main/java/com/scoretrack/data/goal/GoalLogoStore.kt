package com.scoretrack.data.goal

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.scoretrack.ocr.BitmapOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 目标大学卡片的「校徽」图片存储。
 *
 * - 图片复制到应用私有目录 `filesDir/goal_logos/`，不依赖相册原图（相册删图不影响卡片）。
 * - 统一压缩到最长边 512px、JPEG 90，避免卡片列表占用过多内存与备份体积。
 * - 数据库只保存绝对路径；删除目标卡或更换校徽时同步删除旧文件，避免孤儿文件。
 */
class GoalLogoStore(private val context: Context) {

    private fun dir(): File = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** 从相册选择的结果复制一份到私有目录，返回保存后的路径；失败返回 null。 */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = BitmapOps.decode(context, uri, maxDimension = MAX_DIMENSION) ?: return@runCatching null
            val file = File(dir(), "logo_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
            if (!BitmapOps.saveJpeg(bitmap, file.absolutePath, quality = 90)) return@runCatching null
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()
    }

    /** 删除一张校徽文件；传入 null 或文件不存在时静默返回。 */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() && it.isFile }?.delete() }
    }

    /** 清理导入过程中遗留、且没有被任何目标卡引用的文件。 */
    fun cleanup(usedPaths: Collection<String?>) {
        val used = usedPaths.filterNotNull().mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
        runCatching {
            dir().listFiles()?.forEach { file ->
                val canonical = runCatching { file.canonicalPath }.getOrNull()
                if (canonical != null && canonical !in used) file.delete()
            }
        }
    }

    fun exists(path: String?): Boolean =
        !path.isNullOrBlank() && runCatching { File(path).isFile }.getOrDefault(false)

    /** 备份时把校徽读成 Base64；文件不存在或过大时返回 null（备份仍可用，只丢校徽）。 */
    suspend fun readBase64(path: String?): String? = withContext(Dispatchers.IO) {
        if (!exists(path)) return@withContext null
        runCatching {
            val file = File(path!!)
            if (file.length() > MAX_BACKUP_BYTES) return@runCatching null
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }.getOrNull()
    }

    /** 恢复备份时把 Base64 写回私有目录，返回新的绝对路径。 */
    suspend fun writeBase64(cardId: String, base64: String?): String? = withContext(Dispatchers.IO) {
        if (base64.isNullOrBlank()) return@withContext null
        runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            if (bytes.isEmpty() || bytes.size > MAX_BACKUP_BYTES) return@runCatching null
            val file = File(dir(), "logo_restored_$cardId.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    companion object {
        private const val DIR = "goal_logos"
        private const val MAX_DIMENSION = 512
        private const val MAX_BACKUP_BYTES = 600_000L
    }
}
