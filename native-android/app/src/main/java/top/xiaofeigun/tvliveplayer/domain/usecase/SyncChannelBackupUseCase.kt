package top.xiaofeigun.tvliveplayer.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.util.ChannelBackupHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 频道与订阅快照同步：数据变化后（防抖）立即写入快照，
 * 避免清空/删除后未及退到后台就断电，下次启动被旧快照「复活」。
 */
@Singleton
class SyncChannelBackupUseCase @Inject constructor(
    private val repository: ChannelRepository,
    private val backupHelper: ChannelBackupHelper
) {
    /** 持续监听数据变化，须在 [PrepareChannelDataUseCase] 完成后调用，否则会以恢复前的空数据覆盖快照 */
    @OptIn(FlowPreview::class)
    suspend operator fun invoke() {
        combine(repository.getAllChannels(), repository.getAllSources()) { _, _ -> }
            .debounce(DEBOUNCE_MS)
            .collect { saveNow() }
    }

    suspend fun saveNow() {
        try {
            backupHelper.saveBackup(repository.exportData())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 快照为尽力而为，失败不影响使用
        }
    }

    companion object {
        const val DEBOUNCE_MS = 2000L
    }
}
