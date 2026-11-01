package top.xiaofeigun.tvliveplayer.domain.repository

import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.model.Source
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    companion object {
        const val FAVORITE_CATEGORY = "我的收藏"

        /** 分类按首个频道在源中的出现顺序排列（[channels] 已按 sortOrder 排序），有收藏时「我的收藏」置顶 */
        fun categoriesOf(channels: List<Channel>): List<String> {
            val categories = channels.map { it.category }.distinct()
            return if (channels.any { it.isFavorite }) listOf(FAVORITE_CATEGORY) + categories else categories
        }
    }

    fun getAllChannels(): Flow<List<Channel>>
    fun getChannelsByCategory(category: String): Flow<List<Channel>>
    /**
     * 按频道名合并入库。[sourceId] 非空表示这是该订阅的完整内容：
     * 该订阅此前带来、本次已不存在的地址会被清理，清理后无地址的频道随之删除。
     */
    suspend fun addChannels(channels: List<Channel>, sourceId: String? = null)
    /** 按 id 只修改分类字段，避免以界面上的旧快照覆盖刷新后的播放地址 */
    suspend fun moveChannel(channelId: String, category: String)
    suspend fun deleteChannel(channelId: String)
    suspend fun deleteCategory(category: String)
    /** 将 [from] 分类下全部频道并入 [to]，[from] 分类随之消失 */
    suspend fun mergeCategory(from: String, to: String)
    suspend fun toggleFavorite(channelId: String): Boolean

    fun getAllSources(): Flow<List<Source>>
    /** 新增或覆盖（按 id）订阅 */
    suspend fun addSource(source: Source)
    /** 删除订阅并移除它导入的播放地址，仅剩该订阅地址的频道随之删除 */
    suspend fun deleteSource(sourceId: String)

    /** 清空全部频道与订阅源 */
    suspend fun clearAll()
    suspend fun exportData(): String  // JSON
    suspend fun importData(json: String)
    suspend fun resetToDefaults()
}
