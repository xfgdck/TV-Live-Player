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
    fun getAllCategoriesWithFavorite(): Flow<List<String>>
    suspend fun addChannels(channels: List<Channel>)
    suspend fun updateChannel(channel: Channel)
    suspend fun deleteChannel(channel: Channel)
    suspend fun deleteCategory(category: String)
    /** 将 [from] 分类下全部频道并入 [to]，[from] 分类随之消失 */
    suspend fun mergeCategory(from: String, to: String)
    suspend fun toggleFavorite(channelId: String): Boolean

    fun getAllSources(): Flow<List<Source>>
    suspend fun addSource(source: Source)

    /** 清空全部频道与订阅源 */
    suspend fun clearAll()
    suspend fun exportData(): String  // JSON
    suspend fun importData(json: String)
    suspend fun resetToDefaults()
}
