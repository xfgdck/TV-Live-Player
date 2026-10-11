package top.xiaofeigun.tvliveplayer.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.xiaofeigun.tvliveplayer.data.local.dao.ChannelDao
import top.xiaofeigun.tvliveplayer.data.local.dao.SourceDao
import top.xiaofeigun.tvliveplayer.data.local.entity.ChannelEntity
import top.xiaofeigun.tvliveplayer.data.local.entity.SourceEntity
import top.xiaofeigun.tvliveplayer.domain.model.Channel

class ChannelRepositoryTest {

    private lateinit var fakeChannelDao: FakeChannelDao
    private lateinit var repository: ChannelRepositoryImpl

    class FakeChannelDao : ChannelDao {
        val channels = mutableListOf<ChannelEntity>()

        override fun getAllChannels(): Flow<List<ChannelEntity>> = flowOf(channels)
        override suspend fun getAllChannelsList(): List<ChannelEntity> = channels.toList()
        override fun getChannelsByCategory(category: String): Flow<List<ChannelEntity>> =
            flowOf(channels.filter { it.category == category })
        override fun getFavoriteChannels(): Flow<List<ChannelEntity>> =
            flowOf(channels.filter { it.favorite == 1 })
        override fun getCategories(): Flow<List<String>> =
            flowOf(channels.map { it.category }.distinct())
        override suspend fun getChannelById(id: String): ChannelEntity? =
            channels.find { it.id == id }
        override suspend fun getMaxSortOrder(): Int? =
            channels.maxOfOrNull { it.sortOrder }
        override suspend fun insertAll(newChannels: List<ChannelEntity>) {
            channels.addAll(newChannels)
        }
        override suspend fun update(channel: ChannelEntity) {
            val idx = channels.indexOfFirst { it.id == channel.id }
            if (idx != -1) channels[idx] = channel
        }
        override suspend fun updateAll(toUpdate: List<ChannelEntity>) {
            for (ch in toUpdate) {
                val idx = channels.indexOfFirst { it.id == ch.id }
                if (idx != -1) channels[idx] = ch
            }
        }
        override suspend fun delete(channel: ChannelEntity) {
            channels.removeAll { it.id == channel.id }
        }
        override suspend fun deleteAll() {
            channels.clear()
        }
        override fun searchChannels(query: String): Flow<List<ChannelEntity>> =
            flowOf(channels.filter { it.name.contains(query) })
    }

    @Before
    fun setup() {
        fakeChannelDao = FakeChannelDao()
        val fakeSourceDao = object : SourceDao {
            override fun getAllSources(): Flow<List<SourceEntity>> = flowOf(emptyList())
            override suspend fun getSourceById(id: String): SourceEntity? = null
            override suspend fun insert(source: SourceEntity) {}
            override suspend fun delete(source: SourceEntity) {}
            override suspend fun deleteAll() {}
        }
        repository = ChannelRepositoryImpl(fakeChannelDao, fakeSourceDao, Gson())
    }

    @Test
    fun testCrossSourceDeduplicationAndBackupUrlsMerging() = runBlocking {
        // 1. User imports Source 1 (CCTV-1, Hunan TV)
        val source1Channels = listOf(
            Channel(
                id = "cctv1-id",
                name = "CCTV-1",
                url = "http://source1/cctv1.m3u8",
                category = "央视频道",
                isFavorite = true,
                sortOrder = 0
            ),
            Channel(
                id = "hunan-id",
                name = "湖南卫视",
                url = "http://source1/hunan.m3u8",
                category = "卫视频道",
                sortOrder = 1
            )
        )
        repository.addChannels(source1Channels)
        assertEquals(2, fakeChannelDao.channels.size)

        // 2. User imports Source 2 (Contains CCTV-1 with different title and 2 backup URLs, plus Zhejiang TV)
        val source2Channels = listOf(
            Channel(
                id = "cctv1-s2-id",
                name = "CCTV-1 高清",
                url = "http://source2/cctv1.m3u8",
                backupUrls = listOf("http://source2/cctv1_bak.m3u8"),
                category = "央视频道",
                logo = "http://logo.com/cctv1.png"
            ),
            Channel(
                id = "zhejiang-id",
                name = "浙江卫视",
                url = "http://source2/zjws.m3u8",
                category = "卫视频道"
            )
        )
        repository.addChannels(source2Channels)

        // Verifications:
        // Total channels should be 3 (CCTV-1, Hunan, Zhejiang), NOT 4!
        assertEquals(3, fakeChannelDao.channels.size)

        val cctv1 = fakeChannelDao.channels.first { it.id == "cctv1-id" }
        // Original primary URL is preserved
        assertEquals("http://source1/cctv1.m3u8", cctv1.url)
        // User favorite status is preserved!
        assertEquals(1, cctv1.favorite)
        // Sort order is preserved!
        assertEquals(0, cctv1.sortOrder)
        // Missing logo is automatically enriched from Source 2!
        assertEquals("http://logo.com/cctv1.png", cctv1.logo)
        // Backup URLs include both Source 2 primary and Source 2 backup!
        val backups = cctv1.backupUrls.split("|||")
        assertEquals(2, backups.size)
        assertTrue(backups.contains("http://source2/cctv1.m3u8"))
        assertTrue(backups.contains("http://source2/cctv1_bak.m3u8"))

        // Zhejiang TV is appended with sortOrder = 2
        val zj = fakeChannelDao.channels.first { it.name == "浙江卫视" }
        assertEquals(2, zj.sortOrder)
    }

    @Test
    fun testDuplicateUrlPreventionAcrossSources() = runBlocking {
        // Source 1 has URL A
        val s1 = listOf(
            Channel(id = "ch1", name = "CCTV-2", url = "http://same.com/live.m3u8", category = "央视")
        )
        repository.addChannels(s1)

        // Source 2 also has URL A and URL B
        val s2 = listOf(
            Channel(id = "ch2", name = "CCTV-2 财经", url = "http://same.com/live.m3u8", backupUrls = listOf("http://new.com/live.m3u8"), category = "央视")
        )
        repository.addChannels(s2)

        val cctv2 = fakeChannelDao.channels.first()
        assertEquals("http://same.com/live.m3u8", cctv2.url)
        // The duplicate URL A is not duplicated in backupUrls!
        assertEquals("http://new.com/live.m3u8", cctv2.backupUrls)
    }
}
