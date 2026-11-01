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

    override suspend fun addChannels(channels: List<Channel>, sourceId: String?) {
        if (channels.isEmpty()) return
        transactionRunner.run { mergeChannels(channels, sourceId) }
    }

    private suspend fun mergeChannels(channels: List<Channel>, sourceId: String?) {
        val existingEntities = channelDao.getAllChannelsList()
        val existingMap = existingEntities.associateBy { M3UParser.normalizeKey(it.name) }.toMutableMap()
        var nextSortOrder = (channelDao.getMaxSortOrder() ?: -1) + 1

        val toInsert = mutableListOf<ChannelEntity>()
        val toUpdate = mutableMapOf<String, ChannelEntity>()
        val touchedKeys = mutableSetOf<String>()

        for (newCh in channels) {
            val key = M3UParser.normalizeKey(newCh.name)
            val newAllUrls = newCh.allUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (newAllUrls.isEmpty()) continue
            touchedKeys += key
            val existing = existingMap[key]
            if (existing != null) {
                // Channel already exists: merge backup URLs and enrich metadata without duplicating channels
                val existingSources = ChannelEntity.decodeUrlSources(existing.urlSources)
                // 同一订阅刷新时，丢弃该订阅本次已不再提供的地址
                val keptUrls = existing.allUrls()
                    .filter { sourceId == null || existingSources[it] != sourceId || it in newAllUrls }

                // Combine keeping existing primary URL first, appending new unique backup URLs
                val mergedUrls = (keptUrls + newAllUrls).distinct()
                // 已有归属（含手动导入）的地址保持原归属，新地址记到本订阅
                val mergedSources = existingSources.filterKeys { it in mergedUrls } +
                    newAllUrls.filter { it !in keptUrls }.taggedWith(sourceId)

                val updatedEntity = existing.copy(
                    url = mergedUrls.first(),
                    backupUrls = mergedUrls.drop(1).joinToString("|||"),
                    logo = existing.logo?.ifBlank { null } ?: newCh.logo?.ifBlank { null },
                    tvgId = existing.tvgId?.ifBlank { null } ?: newCh.tvgId?.ifBlank { null },
                    category = if (existing.category == "未分类" && newCh.category != "未分类") newCh.category else existing.category,
                    urlSources = ChannelEntity.encodeUrlSources(mergedSources)
                    // favorite and sortOrder are preserved
                )
                existingMap[key] = updatedEntity
                toUpdate[updatedEntity.id] = updatedEntity
            } else {
                // Brand new channel
                val newEntity = ChannelEntity(
                    id = newCh.id,
                    name = newCh.name,
                    url = newAllUrls.first(),
                    backupUrls = newAllUrls.drop(1).joinToString("|||"),
                    category = newCh.category,
                    logo = newCh.logo?.ifBlank { null },
                    tvgId = newCh.tvgId?.ifBlank { null },
                    sortOrder = nextSortOrder++,
                    favorite = if (newCh.isFavorite) 1 else 0,
                    urlSources = ChannelEntity.encodeUrlSources(newAllUrls.taggedWith(sourceId))
                )
                existingMap[key] = newEntity
                toInsert.add(newEntity)
            }
        }

        // 本次订阅内容中已消失的频道：移除该订阅带来的地址，无地址可播的频道直接删除
        if (sourceId != null) {
            val stale = existingMap.filterKeys { it !in touchedKeys }.values
            val (pruned, emptied) = removeSourceUrls(stale, sourceId)
            pruned.forEach { toUpdate[it.id] = it }
            if (emptied.isNotEmpty()) channelDao.deleteChannels(emptied)
        }

        if (toUpdate.isNotEmpty()) {
            channelDao.updateAll(toUpdate.values.toList())
        }
        if (toInsert.isNotEmpty()) {
            channelDao.insertAll(toInsert)
        }
    }

    /** 移除 [entities] 中属于 [sourceId] 的地址；返回（地址减少的频道，地址被清空的频道） */
    private fun removeSourceUrls(
        entities: Collection<ChannelEntity>,
        sourceId: String
    ): Pair<List<ChannelEntity>, List<ChannelEntity>> {
        val pruned = mutableListOf<ChannelEntity>()
        val emptied = mutableListOf<ChannelEntity>()
        for (entity in entities) {
            val sources = ChannelEntity.decodeUrlSources(entity.urlSources)
            val allUrls = entity.allUrls()
            val remaining = allUrls.filter { sources[it] != sourceId }
            when {
                remaining.size == allUrls.size -> Unit
                remaining.isEmpty() -> emptied += entity
                else -> pruned += entity.copy(
                    url = remaining.first(),
                    backupUrls = remaining.drop(1).joinToString("|||"),
                    urlSources = ChannelEntity.encodeUrlSources(sources.filterKeys { it in remaining })
                )
            }
        }
        return pruned to emptied
    }

    private fun List<String>.taggedWith(sourceId: String?): Map<String, String> =
        if (sourceId == null) emptyMap() else associateWith { sourceId }

    override suspend fun moveChannel(channelId: String, category: String) {
        channelDao.updateCategory(channelId, category)
    }

    override suspend fun deleteChannel(channelId: String) {
        channelDao.deleteById(channelId)
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
        channelDao.updateFavorite(channelId, newFav)
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

    override suspend fun deleteSource(sourceId: String) {
        transactionRunner.run {
            val (pruned, emptied) = removeSourceUrls(channelDao.getAllChannelsList(), sourceId)
            if (emptied.isNotEmpty()) channelDao.deleteChannels(emptied)
            if (pruned.isNotEmpty()) channelDao.updateAll(pruned)
            sourceDao.delete(sourceId)
        }
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
