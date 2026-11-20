package com.scoretrack.data.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.scoretrack.data.db.WallpaperDao
import com.scoretrack.data.db.WallpaperSettingEntity
import com.scoretrack.ocr.BitmapOps
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class WallpaperState(
    val path: String? = null,
    val enabled: Boolean = false,
    val opacity: Float = 1f,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val hasImage: Boolean get() = !path.isNullOrBlank() && File(path).exists()
    val visible: Boolean get() = hasImage && opacity > 0.01f
}

/**
 * 壁纸持久化：图片复制到应用私有目录 `filesDir/wallpapers/`，
 * 数据库只保存路径与裁剪参数（缩放、相对位移、透明度），重启后可完整恢复。
 */
class WallpaperRepository(
    private val context: Context,
    private val dao: WallpaperDao,
) {

    private val dir: File get() = File(context.filesDir, "wallpapers").apply { mkdirs() }

    fun observe(): Flow<WallpaperState> = dao.observe().map { it?.toState() ?: WallpaperState() }

    suspend fun current(): WallpaperState = dao.get()?.toState() ?: WallpaperState()

    suspend fun ensureDefault() {
        if (dao.get() == null) dao.upsert(WallpaperSettingEntity(id = 1))
    }

    /**
     * 导入相册/文件管理器图片：解码时按最大边 2560 采样，避免大图 OOM；
     * 成功后删除旧壁纸文件，保证不会无限堆积。
     */
    suspend fun importImage(uri: Uri): Result<WallpaperState> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = BitmapOps.decode(context, uri, maxDimension = 2560)
                ?: error("无法读取所选图片")
            val target = File(dir, "wallpaper_${System.currentTimeMillis()}.jpg")
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            val previous = dao.get()?.wallpaperPath
            val state = WallpaperState(path = target.absolutePath, enabled = true, opacity = 1f)
            dao.upsert(state.toEntity())
            if (!previous.isNullOrBlank() && previous != target.absolutePath) {
                runCatching { File(previous).delete() }
            }
            cleanupOrphans(keep = target.absolutePath)
            state
        }
    }

    suspend fun saveTransform(scale: Float, offsetX: Float, offsetY: Float, opacity: Float) {
        val state = current().copy(
            scale = scale.coerceIn(1f, 4f),
            offsetX = offsetX.coerceIn(-1f, 1f),
            offsetY = offsetY.coerceIn(-1f, 1f),
            opacity = opacity.coerceIn(0f, 1f),
        )
        dao.upsert(state.toEntity())
    }

    suspend fun setEnabled(enabled: Boolean) {
        dao.upsert(current().copy(enabled = enabled).toEntity())
    }

    /** 恢复默认背景：删除图片文件并清空设置。 */
    suspend fun clear() {
        val path = dao.get()?.wallpaperPath
        if (!path.isNullOrBlank()) runCatching { File(path).delete() }
        dao.upsert(WallpaperSettingEntity(id = 1))
        cleanupOrphans(keep = null)
    }

    suspend fun deleteBrokenReference() {
        val path = dao.get()?.wallpaperPath
        if (path != null && !File(path).exists()) {
            dao.upsert(WallpaperSettingEntity(id = 1, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun cleanupOrphans(keep: String?) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath != keep) runCatching { file.delete() }
        }
    }

    private fun WallpaperSettingEntity.toState(): WallpaperState = WallpaperState(
        path = wallpaperPath,
        enabled = enabled,
        opacity = opacity,
        scale = cropScale,
        offsetX = cropOffsetX,
        offsetY = cropOffsetY,
    )

    private fun WallpaperState.toEntity(): WallpaperSettingEntity = WallpaperSettingEntity(
        id = 1,
        wallpaperPath = path,
        enabled = enabled,
        opacity = opacity,
        cropScale = scale,
        cropOffsetX = offsetX,
        cropOffsetY = offsetY,
        updatedAt = System.currentTimeMillis(),
    )
}
