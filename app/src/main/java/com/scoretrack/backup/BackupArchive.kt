package com.scoretrack.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份归档：把 JSON 备份与原始成绩单文件打包为一个 zip。
 * 只包含文件内容，不包含 API Key。
 */
object BackupArchive {

    const val JSON_ENTRY = "scoretrack-backup.json"
    const val DOC_PREFIX = "documents/"

    fun zip(json: String, files: List<File>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.filter { it.exists() && it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry(DOC_PREFIX + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** 读取备份文本：支持纯 JSON 或包含 scoretrack-backup.json 的 zip */
    fun read(bytes: ByteArray): String {
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == JSON_ENTRY || entry.name.endsWith(".json")) {
                        return zip.readBytes().toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                }
            }
            throw IllegalArgumentException("备份压缩包中没有找到 $JSON_ENTRY")
        }
        return bytes.toString(Charsets.UTF_8)
    }

    /** 恢复 zip 中的原始成绩单文件到应用私有目录，返回 文件名 -> 绝对路径 */
    fun restoreDocuments(bytes: ByteArray, targetDir: File): Map<String, String> {
        if (bytes.size <= 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return emptyMap()
        if (!targetDir.exists()) targetDir.mkdirs()
        val result = HashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.startsWith(DOC_PREFIX)) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name.removePrefix(DOC_PREFIX).substringAfterLast('/')
                if (name.isNotBlank()) {
                    val target = File(targetDir, name)
                    target.outputStream().use { output -> zip.copyTo(output) }
                    result[name] = target.absolutePath
                }
                zip.closeEntry()
            }
        }
        return result
    }
}
