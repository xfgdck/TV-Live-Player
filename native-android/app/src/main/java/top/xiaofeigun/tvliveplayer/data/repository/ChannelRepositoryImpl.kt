package top.xiaofeigun.tvliveplayer.data.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import top.xiaofeigun.tvliveplayer.data.local.TransactionRunner
import top.xiaofeigun.tvliveplayer.data.local.dao.ChannelDao
import top.xiaofeigun.tvliveplayer.data.local.dao.SourceDao
import top.xiaofeigun.tvliveplayer.data.local.entity.ChannelEntity
import top.xiaofeigun.tvliveplayer.data.local.entity.SourceEntity
import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.model.Source
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.parser.M3UParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val gson: Gson,
    private val transactionRunner: TransactionRunner
) : ChannelRepository {

    override fun getAllChannels(): Flow<List<Channel>> {
        return channelDao.getAllChannels().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getChannelsByCategory(category: String): Flow<List<Channel>> {
        if (category == ChannelRepository.FAVORITE_CATEGORY) {
            return channelDao.getFavoriteChannels().map { entities ->
                entities.map { it.toDomainModel() }
            }
        }
        return channelDao.getChannelsByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getAllCategoriesWithFavorite(): Flow<List<String>> {
        return combine(channelDao.getCategories(), channelDao.getFavoriteChannels()) { categories, favorites ->
            if (favorites.isNotEmpty()) {
                listOf(ChannelRepository.FAVORITE_CATEGORY) + categories
            } else {
                categories
            }
        }
    }

    override suspend fun addChannels(channels: List<Channel>) {
        if (channels.isEmpty()) return
        transactionRunner.run { mergeChannels(channels) }
    }

    private suspend fun mergeChannels(channels: List<Channel>) {
        val existingEntities = channelDao.getAllChannelsList()
        val existingMap = existingEntities.associateBy { M3UParser.normalizeKey(it.name) }.toMutableMap()
        var nextSortOrder = (channelDao.getMaxSortOrder() ?: -1) + 1

        val toInsert = mutableListOf<ChannelEntity>()
        val toUpdate = mutableMapOf<String, ChannelEntity>()

        for (newCh in channels) {
            val key = M3UParser.normalizeKey(newCh.name)
            val existing = existingMap[key]
            if (existing != null) {
                // Channel already exists: merge backup URLs and enrich metadata without duplicating channels
                val existingBackup = if (existing.backupUrls.isBlank()) emptyList() else existing.backupUrls.split("|||")
                val existingAllUrls = listOf(existing.url) + existingBackup
                val newAllUrls = listOf(newCh.url) + newCh.backupUrls

                // Combine keeping existing primary URL first, appending new unique backup URLs
                val mergedUrls = (existingAllUrls + newAllUrls)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                val updatedEntity = existing.copy(
                    url = mergedUrls.firstOrNull() ?: existing.url,
                    backupUrls = mergedUrls.drop(1).joinToString("|||"),
                    logo = existing.logo?.ifBlank { null } ?: newCh.logo?.ifBlank { null },
                    tvgId = existing.tvgId?.ifBlank { null } ?: newCh.tvgId?.ifBlank { null },
                    category = if (existing.category == "未分类" && newCh.category != "未分类") newCh.category else existing.category
                    // favorite and sortOrder are preserved
                )
                existingMap[key] = updatedEntity
                toUpdate[updatedEntity.id] = updatedEntity
            } else {
                // Brand new channel
                val newEntity = ChannelEntity(
                    id = newCh.id,
                    name = newCh.name,
                    url = newCh.url,
                    backupUrls = newCh.backupUrls.filter { it.isNotBlank() }.distinct().joinToString("|||"),
                    category = newCh.category,
                    logo = newCh.logo?.ifBlank { null },
                    tvgId = newCh.tvgId?.ifBlank { null },
                    sortOrder = nextSortOrder++,
                    favorite = if (newCh.isFavorite) 1 else 0
                )
                existingMap[key] = newEntity
                toInsert.add(newEntity)
            }
        }

        if (toUpdate.isNotEmpty()) {
            channelDao.updateAll(toUpdate.values.toList())
        }
        if (toInsert.isNotEmpty()) {
            channelDao.insertAll(toInsert)
        }
    }

    override suspend fun updateChannel(channel: Channel) {
        channelDao.update(ChannelEntity.fromDomainModel(channel))
    }

    override suspend fun deleteChannel(channel: Channel) {
        channelDao.delete(ChannelEntity.fromDomainModel(channel))
    }

    override suspend fun deleteCategory(category: String) {
        if (category == ChannelRepository.FAVORITE_CATEGORY) return
        channelDao.deleteByCategory(category)
    }

    override suspend fun mergeCategory(from: String, to: String) {
        if (from == to || from == ChannelRepository.FAVORITE_CATEGORY || to == ChannelRepository.FAVORITE_CATEGORY) return
        channelDao.moveCategory(from, to)
    }

    override suspend fun toggleFavorite(channelId: String): Boolean {
        val entity = channelDao.getChannelById(channelId) ?: return false
        val newFav = if (entity.favorite == 1) 0 else 1
        channelDao.update(entity.copy(favorite = newFav))
        return newFav == 1
    }

    override fun getAllSources(): Flow<List<Source>> {
        return sourceDao.getAllSources().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun addSource(source: Source) {
        sourceDao.insert(SourceEntity.fromDomainModel(source))
    }

    override suspend fun clearAll() {
        transactionRunner.run {
            channelDao.deleteAll()
            sourceDao.deleteAll()
        }
    }

    override suspend fun exportData(): String {
        val channels = channelDao.getAllChannels().first().map { it.toDomainModel() }
        val sources = sourceDao.getAllSources().first().map { it.toDomainModel() }

        val export = ExportData(
            channels = channels,
            sources = sources
        )
        return gson.toJson(export)
    }

    override suspend fun importData(json: String) {
        val export = gson.fromJson(json, ExportData::class.java) ?: return

        // 清空与写入在同一事务中完成，避免 UI 观察到中间态空列表
        transactionRunner.run {
            channelDao.deleteAll()
            sourceDao.deleteAll()
            if (export.channels.isNotEmpty()) {
                channelDao.insertAll(export.channels.map { ChannelEntity.fromDomainModel(it) })
            }
            export.sources.forEach { sourceDao.insert(SourceEntity.fromDomainModel(it)) }
        }
    }

    override suspend fun resetToDefaults() {
        transactionRunner.run {
            channelDao.deleteAll()
            sourceDao.deleteAll()
            val defaultChannels = DefaultChannels.getDefaultChannels()
            if (defaultChannels.isNotEmpty()) {
                channelDao.insertAll(defaultChannels.map { ChannelEntity.fromDomainModel(it) })
            }
        }
    }

    /**
     * Data class used for JSON serialization/deserialization of exported data.
     */
    private data class ExportData(
        @SerializedName("channels")
        val channels: List<Channel>,
        @SerializedName("sources")
        val sources: List<Source>
    )
}
