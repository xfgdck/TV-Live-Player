package com.wangyg.tvliveplayer.data.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.wangyg.tvliveplayer.data.local.dao.ChannelDao
import com.wangyg.tvliveplayer.data.local.dao.SourceDao
import com.wangyg.tvliveplayer.data.local.entity.ChannelEntity
import com.wangyg.tvliveplayer.data.local.entity.SourceEntity
import com.wangyg.tvliveplayer.data.preferences.AppPreferences
import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.domain.model.Source
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val appPreferences: AppPreferences,
    private val gson: Gson
) : ChannelRepository {

    override fun getAllChannels(): Flow<List<Channel>> {
        return channelDao.getAllChannels().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getChannelsByCategory(category: String): Flow<List<Channel>> {
        return channelDao.getChannelsByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getCategories(): Flow<List<String>> {
        return channelDao.getCategories()
    }

    override fun searchChannels(query: String): Flow<List<Channel>> {
        return channelDao.searchChannels(query).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getChannelById(id: String): Channel? {
        return channelDao.getChannelById(id)?.toDomainModel()
    }

    override suspend fun addChannels(channels: List<Channel>) {
        val entities = channels.map { ChannelEntity.fromDomainModel(it) }
        channelDao.insertAll(entities)
    }

    override suspend fun updateChannel(channel: Channel) {
        channelDao.update(ChannelEntity.fromDomainModel(channel))
    }

    override suspend fun deleteChannel(channel: Channel) {
        channelDao.delete(ChannelEntity.fromDomainModel(channel))
    }

    override suspend fun clearAllChannels() {
        channelDao.deleteAll()
    }

    override suspend fun toggleFavorite(channelId: String) {
        val entity = channelDao.getChannelById(channelId) ?: return
        val updated = entity.copy(favorite = if (entity.favorite == 1) 0 else 1)
        channelDao.update(updated)
    }

    override fun getAllSources(): Flow<List<Source>> {
        return sourceDao.getAllSources().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun addSource(source: Source) {
        sourceDao.insert(SourceEntity.fromDomainModel(source))
    }

    override suspend fun deleteSource(source: Source) {
        sourceDao.delete(SourceEntity.fromDomainModel(source))
    }

    override suspend fun clearAllSources() {
        sourceDao.deleteAll()
    }

    override suspend fun exportData(): String {
        val channelEntities = channelDao.getAllChannels().map { entities ->
            entities.map { it.toDomainModel() }
        }
        val sourceEntities = sourceDao.getAllSources().map { entities ->
            entities.map { it.toDomainModel() }
        }

        // Since Flow cannot be directly collected in a non-flow context,
        // we use a helper to collect the first emission
        val channels = collectFirst(channelEntities) ?: emptyList()
        val sources = collectFirst(sourceEntities) ?: emptyList()

        val export = ExportData(
            channels = channels,
            sources = sources
        )
        return gson.toJson(export)
    }

    override suspend fun importData(json: String) {
        val export = gson.fromJson(json, ExportData::class.java) ?: return

        // Clear existing data
        channelDao.deleteAll()
        sourceDao.deleteAll()

        // Import channels
        if (export.channels.isNotEmpty()) {
            val channelEntities = export.channels.map { ChannelEntity.fromDomainModel(it) }
            channelDao.insertAll(channelEntities)
        }

        // Import sources
        export.sources.forEach { source ->
            sourceDao.insert(SourceEntity.fromDomainModel(source))
        }
    }

    override suspend fun resetToDefaults() {
        channelDao.deleteAll()
        sourceDao.deleteAll()

        val defaultChannels = DefaultChannels.getDefaultChannels()
        if (defaultChannels.isNotEmpty()) {
            val entities = defaultChannels.map { ChannelEntity.fromDomainModel(it) }
            channelDao.insertAll(entities)
        }
    }

    /**
     * Helper to collect the first emission from a Flow in a suspend context.
     */
    private suspend fun <T> collectFirst(flow: Flow<T>): T? {
        var result: T? = null
        flow.collect { value ->
            if (result == null) {
                result = value
            }
        }
        return result
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
