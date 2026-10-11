package top.xiaofeigun.tvliveplayer.domain.repository

import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.model.Source
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    companion object {
        const val FAVORITE_CATEGORY = "我的收藏"
    }

    fun getAllChannels(): Flow<List<Channel>>
    fun getChannelsByCategory(category: String): Flow<List<Channel>>
    fun getCategories(): Flow<List<String>>
    fun getAllCategoriesWithFavorite(): Flow<List<String>>
    fun searchChannels(query: String): Flow<List<Channel>>
    suspend fun getChannelById(id: String): Channel?
    suspend fun addChannels(channels: List<Channel>)
    suspend fun updateChannel(channel: Channel)
    suspend fun deleteChannel(channel: Channel)
    suspend fun clearAllChannels()
    suspend fun isFavorite(channelId: String): Boolean
    suspend fun addFavorite(channelId: String)
    suspend fun removeFavorite(channelId: String)
    suspend fun toggleFavorite(channelId: String): Boolean

    fun getAllSources(): Flow<List<Source>>
    suspend fun addSource(source: Source)
    suspend fun deleteSource(source: Source)
    suspend fun clearAllSources()

    suspend fun exportData(): String  // JSON
    suspend fun importData(json: String)
    suspend fun resetToDefaults()
}
