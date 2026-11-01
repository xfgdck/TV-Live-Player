package top.xiaofeigun.tvliveplayer.domain.usecase

import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.parser.M3UParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParseAndImportM3UUseCase @Inject constructor(
    private val parser: M3UParser,
    private val repository: ChannelRepository
) {
    /** [sourceId] 为订阅 id 时按该订阅的完整内容同步（清理失效地址），见 [ChannelRepository.addChannels] */
    suspend operator fun invoke(content: String, category: String? = null, sourceId: String? = null): Int {
        val channels = parser.parse(content, category)
        if (channels.isNotEmpty()) {
            repository.addChannels(channels, sourceId)
        }
        return channels.size
    }
}
