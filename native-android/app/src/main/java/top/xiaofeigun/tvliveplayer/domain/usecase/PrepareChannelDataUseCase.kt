package top.xiaofeigun.tvliveplayer.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.xiaofeigun.tvliveplayer.data.preferences.AppPreferences
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.util.ChannelBackupHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启动数据准备：首次启动初始化默认频道；数据库为空（如覆盖更新后）时从快照自愈恢复。
 * 播放器须在此完成后再加载频道，否则会先看到空列表，误跳源管理并丢失上次频道记忆。
 */
@Singleton
class PrepareChannelDataUseCase @Inject constructor(
    private val repository: ChannelRepository,
    private val appPreferences: AppPreferences,
    private val backupHelper: ChannelBackupHelper
) {
    private val mutex = Mutex()
    private var prepared = false

    suspend operator fun invoke() = mutex.withLock {
        if (prepared) return@withLock
        try {
            if (appPreferences.isFirstLaunch()) {
                repository.resetToDefaults()
                appPreferences.setFirstLaunchDone()
                backupHelper.saveBackup(repository.exportData())
            } else if (repository.getAllChannels().first().isEmpty()) {
                backupHelper.loadBackup()?.takeIf { it.isNotBlank() }?.let { repository.importData(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 快照损坏等异常不阻塞启动，按现有数据继续
        }
        prepared = true
    }
}
