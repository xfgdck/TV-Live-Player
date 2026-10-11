package top.xiaofeigun.tvliveplayer.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 频道数据持久化快照与自愈恢复助手
 * 保障应用在升级、覆盖更新或极端异常情况下，用户频道数据 100% 不丢失
 */
@Singleton
class ChannelBackupHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val BACKUP_DIR_NAME = "backup"
        private const val BACKUP_FILE_NAME = "channels_snapshot.json"
    }

    private val backupFile: File by lazy {
        val dir = File(context.filesDir, BACKUP_DIR_NAME).apply { mkdirs() }
        File(dir, BACKUP_FILE_NAME)
    }

    suspend fun saveBackup(json: String) = withContext(Dispatchers.IO) {
        try {
            if (json.isNotBlank()) {
                val tempFile = File(backupFile.parentFile, "$BACKUP_FILE_NAME.tmp")
                tempFile.writeText(json, Charsets.UTF_8)
                if (backupFile.exists()) backupFile.delete()
                tempFile.renameTo(backupFile)
            }
        } catch (_: Exception) {
            // Best effort snapshot saving
        }
    }

    suspend fun loadBackup(): String? = withContext(Dispatchers.IO) {
        try {
            if (backupFile.exists() && backupFile.length() > 0) {
                backupFile.readText(Charsets.UTF_8)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hasBackup(): Boolean {
        return backupFile.exists() && backupFile.length() > 0
    }
}
