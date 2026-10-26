package top.xiaofeigun.tvliveplayer.domain.usecase

import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import top.xiaofeigun.tvliveplayer.data.remote.PlaylistFetcher
import top.xiaofeigun.tvliveplayer.domain.model.Source
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import javax.inject.Inject
import javax.inject.Singleton

data class RefreshResult(val succeeded: Int, val failed: Int, val channelCount: Int)

/** 在线订阅导入与刷新：下载 → 解析合并入库 → 记录订阅地址供后续一键刷新 */
@Singleton
class ImportSubscriptionUseCase @Inject constructor(
    private val fetcher: PlaylistFetcher,
    private val parseAndImport: ParseAndImportM3UUseCase,
    private val repository: ChannelRepository
) {
    /** 导入单个订阅，返回解析出的频道数；网络或格式异常时抛出 */
    suspend operator fun invoke(url: String): Int {
        val count = parseAndImport(fetcher.fetch(url))
        if (count > 0) {
            repository.addSource(Source(id = url, name = url.toHttpUrlOrNull()?.host ?: url, url = url))
        }
        return count
    }

    /** 依次刷新全部已保存订阅，单个失败不影响其余订阅 */
    suspend fun refreshAll(): RefreshResult {
        var succeeded = 0
        var failed = 0
        var channelCount = 0
        for (source in repository.getAllSources().first()) {
            try {
                channelCount += parseAndImport(fetcher.fetch(source.url))
                succeeded++
            } catch (_: Exception) {
                failed++
            }
        }
        return RefreshResult(succeeded, failed, channelCount)
    }
}
