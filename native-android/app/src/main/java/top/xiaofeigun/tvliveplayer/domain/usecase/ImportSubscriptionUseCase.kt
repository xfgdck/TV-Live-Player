package top.xiaofeigun.tvliveplayer.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import top.xiaofeigun.tvliveplayer.data.remote.PlaylistFetcher
import top.xiaofeigun.tvliveplayer.domain.model.Source
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class RefreshResult(val succeeded: Int, val failed: Int, val channelCount: Int)

/** 在线订阅导入与刷新：下载 → 解析合并入库 → 记录订阅地址与更新状态供后续刷新 */
@Singleton
class ImportSubscriptionUseCase @Inject constructor(
    private val fetcher: PlaylistFetcher,
    private val parseAndImport: ParseAndImportM3UUseCase,
    private val repository: ChannelRepository
) {
    /** 导入单个订阅，返回解析出的频道数；网络或格式异常时抛出。重复导入同一地址等同于更新该订阅 */
    suspend operator fun invoke(url: String): Int {
        val count = parseAndImport(fetcher.fetch(url), sourceId = url)
        if (count > 0) {
            val existing = repository.getAllSources().first().find { it.id == url }
            val source = existing ?: Source(id = url, name = url.toHttpUrlOrNull()?.host ?: url, url = url)
            repository.addSource(source.updatedWith(count))
        }
        return count
    }

    /** 刷新单个订阅并记录结果；失败时记录原因后抛出 */
    suspend fun refresh(source: Source): Int {
        try {
            val count = parseAndImport(fetcher.fetch(source.url), sourceId = source.id)
            if (count == 0) throw IOException("未解析到任何频道")
            if (!updateSource(source.id) { it.updatedWith(count) }) {
                // 更新期间订阅已被删除：清理本次导入的地址
                repository.deleteSource(source.id)
            }
            return count
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateSource(source.id) { it.copy(lastError = e.localizedMessage ?: "网络错误") }
            throw e
        }
    }

    /** 以库中最新状态为基础写回，避免覆盖更新期间的重命名；订阅已删除时不写回并返回 false */
    private suspend fun updateSource(id: String, transform: (Source) -> Source): Boolean {
        val latest = repository.getAllSources().first().find { it.id == id } ?: return false
        repository.addSource(transform(latest))
        return true
    }

    /** 启动时静默刷新超过 [AUTO_REFRESH_INTERVAL_MS] 未成功更新的订阅 */
    suspend fun refreshStale(now: Long = System.currentTimeMillis()): RefreshResult =
        refreshEach(repository.getAllSources().first().filter { now - it.lastUpdated >= AUTO_REFRESH_INTERVAL_MS })

    /** 依次刷新，单个失败不影响其余订阅 */
    private suspend fun refreshEach(sources: List<Source>): RefreshResult {
        var succeeded = 0
        var failed = 0
        var channelCount = 0
        for (source in sources) {
            try {
                channelCount += refresh(source)
                succeeded++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed++
            }
        }
        return RefreshResult(succeeded, failed, channelCount)
    }

    private fun Source.updatedWith(count: Int) =
        copy(lastUpdated = System.currentTimeMillis(), channelCount = count, lastError = null)

    companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
