package com.wangyg.tvliveplayer.domain.usecase

import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import com.wangyg.tvliveplayer.parser.M3UParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParseAndImportM3UUseCase @Inject constructor(
    private val parser: M3UParser,
    private val repository: ChannelRepository
) {
    suspend operator fun invoke(content: String, category: String? = null): Int {
        val channels = parser.parse(content, category)
        if (channels.isNotEmpty()) {
            repository.addChannels(channels)
        }
        return channels.size
    }
}
