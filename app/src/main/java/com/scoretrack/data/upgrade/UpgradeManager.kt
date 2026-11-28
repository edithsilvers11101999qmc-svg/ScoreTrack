package com.scoretrack.data.upgrade

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * 升级安全机制：
 * 1. Room 打开数据库之前，读取磁盘上的 `PRAGMA user_version`；
 * 2. 若低于当前版本，先把数据库（含 -wal/-shm）、壁纸文件、设置快照复制到
 *    `filesDir/upgrade_backups/v{旧}_to_v{新}_{时间}/`；
 * 3. 迁移完成后（Room onOpen 回调）对比迁移前后数量，写入 `app_settings` 供界面展示；
 * 4. 迁移失败时不删除任何原文件，备份目录保留可人工恢复。
 */
object UpgradeManager {

    const val CURRENT_DB_VERSION = 5
    const val SETTING_LAST_RESULT = "upgrade.last_result"
    const val SETTING_PENDING_NOTICE = "upgrade.pending_notice"
    private const val BACKUP_ROOT = "upgrade_backups"

    data class Pending(
        val fromVersion: Int,
        val toVersion: Int,
        val backupDir: String?,
        val before: Map<String, Int>,
        val at: Long,
    )

    /** 返回非空表示本次启动将执行升级，并已完成升级前备份。 */
    fun prepareIfNeeded(context: Context): Pending? {
        val dbFile = context.getDatabasePath("scoretrack.db")
        if (!dbFile.exists()) return null
        val version = readUserVersion(dbFile) ?: return null
        if (version >= CURRENT_DB_VERSION) return null

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(context.filesDir, "$BACKUP_ROOT/v${version}_to_v${CURRENT_DB_VERSION}_$stamp")
        val before = readCounts(dbFile)
        var backupOk = false
        try {
            dir.mkdirs()
            copyIfExists(dbFile, File(dir, "scoretrack.db"))
            copyIfExists(File(dbFile.path + "-wal"), File(dir, "scoretrack.db-wal"))
            copyIfExists(File(dbFile.path + "-shm"), File(dir, "scoretrack.db-shm"))
            copyDir(File(context.filesDir, "wallpapers"), File(dir, "wallpapers"))
            writeSettingsSnapshot(context, File(dir, "settings.txt"))
            backupOk = true
        } catch (t: Throwable) {
            // 备份失败不阻止升级（否则用户永远无法启动新版本），但会记录状态并在界面提示
            runCatching { File(dir, "backup_error.txt").writeText(t.toString()) }
        }

        val manifest = JSONObject().apply {
            put("fromVersion", version)
            put("toVersion", CURRENT_DB_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("backupOk", backupOk)
            put("before", JSONObject(before.toMap()))
        }
        runCatching { File(dir, "manifest.json").writeText(manifest.toString(2)) }

        return Pending(
            fromVersion = version,
            toVersion = CURRENT_DB_VERSION,
            backupDir = if (backupOk) dir.absolutePath else null,
            before = before,
            at = System.currentTimeMillis(),
        )
    }

    /** Room 打开数据库后调用：迁移已完成，校验数据并写入升级结果。 */
    fun finish(context: Context, db: SupportSQLiteDatabase, pending: Pending) {
        val after = counts(db)
        val ok = listOf("students", "exams", "scores", "ai_reports", "goal_cards").all { table ->
            after[table].orZero() >= pending.before[table].orZero()
        }
        val result = JSONObject().apply {
            put("fromVersion", pending.fromVersion)
            put("toVersion", pending.toVersion)
            put("at", pending.at)
            put("ok", ok)
            put("backupDir", pending.backupDir ?: "")
            put("before", JSONObject(pending.before.toMap()))
            put("after", JSONObject(after.toMap()))
        }
        runCatching {
            db.execSQL(
                "INSERT OR REPLACE INTO app_settings (`key`, `value`) VALUES (?, ?)",
                arrayOf(SETTING_LAST_RESULT, result.toString()),
            )
            db.execSQL(
                "INSERT OR REPLACE INTO app_settings (`key`, `value`) VALUES (?, ?)",
                arrayOf(SETTING_PENDING_NOTICE, "1"),
            )
        }
        runCatching {
            File(context.filesDir, "$BACKUP_ROOT/last_result.json").writeText(result.toString(2))
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun readUserVersion(file: File): Int? = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.version
        }
    }.getOrNull()

    private fun readCounts(file: File): Map<String, Int> = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            countsOf { sql -> db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }
        }
    }.getOrDefault(emptyMap())

    private fun counts(db: SupportSQLiteDatabase): Map<String, Int> =
        countsOf { sql -> db.query(sql).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }

    private fun countsOf(query: (String) -> Int): Map<String, Int> = linkedMapOf(
        "students" to query("SELECT COUNT(*) FROM students"),
        "exams" to query("SELECT COUNT(*) FROM exams"),
        "scores" to query("SELECT COUNT(*) FROM scores"),
        "ai_reports" to query("SELECT COUNT(*) FROM ai_reports"),
        "goal_cards" to runCatching { query("SELECT COUNT(*) FROM goal_cards") }.getOrDefault(0),
        "subject_selections" to runCatching { query("SELECT COUNT(*) FROM subject_selections") }.getOrDefault(0),
        "countdown_exams" to runCatching { query("SELECT COUNT(*) FROM countdown_exams") }.getOrDefault(0),
    )

    private fun copyIfExists(from: File, to: File) {
        if (from.exists()) {
            to.parentFile?.mkdirs()
            from.copyTo(to, overwrite = true)
        }
    }

    private fun copyDir(from: File, to: File) {
        if (!from.isDirectory) return
        to.mkdirs()
        from.listFiles()?.forEach { file ->
            if (file.isFile) file.copyTo(File(to, file.name), overwrite = true)
        }
    }

    private fun writeSettingsSnapshot(context: Context, out: File) {
        val dbFile = context.getDatabasePath("scoretrack.db")
        val rows = runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT `key`, `value` FROM app_settings", null).use { c ->
                    buildList {
                        while (c.moveToNext()) add(c.getString(0) to c.getString(1))
                    }
                }
            }
        }.getOrDefault(emptyList())
        out.writeText(rows.joinToString("\n") { "${it.first}=${it.second}" })
    }
}
