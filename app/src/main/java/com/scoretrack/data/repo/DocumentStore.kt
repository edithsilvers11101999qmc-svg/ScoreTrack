package com.scoretrack.data.repo

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 原始成绩单文件本地留档。文件保存在应用私有目录 files/documents 下，
 * 备份时一并打包；删除文件不会级联删除已确认的成绩记录。
 */
class DocumentStore(private val context: Context) {

    fun documentsDir(): File = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }

    fun copy(uris: List<Uri>, baseName: String): List<File> {
        val dir = documentsDir()
        val result = ArrayList<File>()
        uris.forEachIndexed { index, uri ->
            val name = queryName(uri) ?: "source_${System.currentTimeMillis()}_$index"
            val extension = name.substringAfterLast('.', "").lowercase()
            val target = File(dir, baseName + "_" + index + if (extension.isNotBlank()) ".$extension" else "")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.exists() && target.length() > 0) result.add(target)
            }
        }
        return result
    }

    fun delete(paths: List<String>) {
        paths.forEach { path ->
            runCatching { File(path).takeIf { it.exists() }?.delete() }
        }
    }

    fun files(): List<File> = documentsDir().listFiles()?.toList() ?: emptyList()

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
