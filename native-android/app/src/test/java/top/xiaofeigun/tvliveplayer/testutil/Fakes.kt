package top.xiaofeigun.tvliveplayer.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import top.xiaofeigun.tvliveplayer.data.local.TransactionRunner
import top.xiaofeigun.tvliveplayer.data.local.dao.ChannelDao
import top.xiaofeigun.tvliveplayer.data.local.dao.SourceDao
import top.xiaofeigun.tvliveplayer.data.local.entity.ChannelEntity
import top.xiaofeigun.tvliveplayer.data.local.entity.SourceEntity
import top.xiaofeigun.tvliveplayer.data.preferences.AppPreferences

/** 响应式内存 ChannelDao：与 Room 一样，数据变更后所有 Flow 重新推送 */
class FakeChannelDao : ChannelDao {
    private val data = MutableStateFlow<List<ChannelEntity>>(emptyList())

    val channels: List<ChannelEntity> get() = data.value

    fun seed(list: List<ChannelEntity>) {
        data.value = list
    }

    override fun getAllChannels(): Flow<List<ChannelEntity>> = data.map { it.sortedBy { ch -> ch.sortOrder } }
    override fun getChannelsByCategory(category: String): Flow<List<ChannelEntity>> =
        data.map { list -> list.filter { it.category == category }.sortedBy { it.sortOrder } }
    override fun getFavoriteChannels(): Flow<List<ChannelEntity>> =
        data.map { list -> list.filter { it.favorite == 1 }.sortedBy { it.sortOrder } }
    override fun getCategories(): Flow<List<String>> =
        data.map { list -> list.sortedBy { it.sortOrder }.map { it.category }.distinct() }
    override suspend fun getChannelById(id: String): ChannelEntity? = data.value.find { it.id == id }
    override suspend fun getAllChannelsList(): List<ChannelEntity> = data.value.sortedBy { it.sortOrder }
    override suspend fun getMaxSortOrder(): Int? = data.value.maxOfOrNull { it.sortOrder }
    override suspend fun insertAll(channels: List<ChannelEntity>) {
        val ids = channels.map { it.id }.toSet()
        data.value = data.value.filter { it.id !in ids } + channels
    }
    override suspend fun update(channel: ChannelEntity) = updateAll(listOf(channel))
    override suspend fun updateAll(channels: List<ChannelEntity>) {
        val byId = channels.associateBy { it.id }
        data.value = data.value.map { byId[it.id] ?: it }
    }
    override suspend fun delete(channel: ChannelEntity) {
        data.value = data.value.filter { it.id != channel.id }
    }
    override suspend fun deleteAll() {
        data.value = emptyList()
    }
    override suspend fun deleteByCategory(category: String) {
        data.value = data.value.filter { it.category != category }
    }
    override suspend fun moveCategory(from: String, to: String) {
        data.value = data.value.map { if (it.category == from) it.copy(category = to) else it }
    }
}

class FakeSourceDao : SourceDao {
    private val data = MutableStateFlow<List<SourceEntity>>(emptyList())

    override fun getAllSources(): Flow<List<SourceEntity>> = data
    override suspend fun insert(source: SourceEntity) {
        data.value = data.value.filter { it.id != source.id } + source
    }
    override suspend fun deleteAll() {
        data.value = emptyList()
    }
}

object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

class FakeAppPreferences : AppPreferences(null) {
    private var lastId: String? = null
    override fun getLastChannelId(): String? = lastId
    override fun setLastChannelId(id: String) { lastId = id }
    override fun isFirstLaunch(): Boolean = false
    override fun setFirstLaunchDone() {}
}
