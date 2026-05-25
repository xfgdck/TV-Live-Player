package com.wangyg.tvliveplayer.domain.repository

import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.domain.model.Source
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    fun getAllChannels(): Flow<List<Channel>>
    fun getChannelsByCategory(category: String): Flow<List<Channel>>
    fun getCategories(): Flow<List<String>>
    fun searchChannels(query: String): Flow<List<Channel>>
    suspend fun getChannelById(id: String): Channel?
    suspend fun addChannels(channels: List<Channel>)
    suspend fun updateChannel(channel: Channel)
    suspend fun deleteChannel(channel: Channel)
    suspend fun clearAllChannels()
    suspend fun toggleFavorite(channelId: String)

    fun getAllSources(): Flow<List<Source>>
    suspend fun addSource(source: Source)
    suspend fun deleteSource(source: Source)
    suspend fun clearAllSources()

    suspend fun exportData(): String  // JSON
    suspend fun importData(json: String)
    suspend fun resetToDefaults()
}
