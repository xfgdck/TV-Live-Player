package top.xiaofeigun.tvliveplayer.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.xiaofeigun.tvliveplayer.data.local.entity.ChannelEntity
import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.model.Source
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.testutil.FakeChannelDao
import top.xiaofeigun.tvliveplayer.testutil.FakeSourceDao
import top.xiaofeigun.tvliveplayer.testutil.ImmediateTransactionRunner

class ChannelRepositoryTest {

    private lateinit var fakeChannelDao: FakeChannelDao
    private lateinit var repository: ChannelRepositoryImpl

    @Before
    fun setup() {
        fakeChannelDao = FakeChannelDao()
        repository = ChannelRepositoryImpl(fakeChannelDao, FakeSourceDao(), Gson(), ImmediateTransactionRunner)
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

    private fun seed() {
        fakeChannelDao.seed(
            listOf(
                ChannelEntity(id = "a1", name = "CCTV-1", url = "http://a1", category = "央视", sortOrder = 0),
                ChannelEntity(id = "b1", name = "湖南卫视", url = "http://b1", category = "卫视", sortOrder = 1, favorite = 1),
                ChannelEntity(id = "a2", name = "CCTV-2", url = "http://a2", category = "央视", sortOrder = 2),
                ChannelEntity(id = "c1", name = "本地台", url = "http://c1", category = "地方", sortOrder = 3)
            )
        )
    }

    @Test
    fun testCategoriesKeepSourceOrderWithFavoriteFirst() = runBlocking {
        seed()
        assertEquals(
            listOf(ChannelRepository.FAVORITE_CATEGORY, "央视", "卫视", "地方"),
            repository.getAllCategoriesWithFavorite().first()
        )
    }

    @Test
    fun testMergeCategoryMovesAllChannels() = runBlocking {
        seed()
        repository.mergeCategory("地方", "央视")
        assertEquals(listOf("a1", "a2", "c1"), repository.getChannelsByCategory("央视").first().map { it.id })
        assertTrue("地方" !in repository.getAllCategoriesWithFavorite().first())
        // 收藏是虚拟分类，不可作为合并源或目标
        repository.mergeCategory(ChannelRepository.FAVORITE_CATEGORY, "地方")
        assertEquals(1, repository.getChannelsByCategory(ChannelRepository.FAVORITE_CATEGORY).first().size)
    }

    @Test
    fun testDeleteCategoryRemovesOnlyThatCategory() = runBlocking {
        seed()
        repository.deleteCategory("央视")
        assertEquals(listOf("b1", "c1"), fakeChannelDao.channels.map { it.id })
    }

    @Test
    fun testExportImportRoundTripReplacesData() = runBlocking {
        seed()
        repository.addSource(Source(id = "http://s", name = "s", url = "http://s"))
        val json = repository.exportData()
        repository.clearAll()
        assertTrue(fakeChannelDao.channels.isEmpty())
        assertTrue(repository.getAllSources().first().isEmpty())

        repository.importData(json)
        assertEquals(4, fakeChannelDao.channels.size)
        assertEquals(1, fakeChannelDao.channels.first { it.id == "b1" }.favorite)
        assertEquals(listOf("http://s"), repository.getAllSources().first().map { it.url })
    }
}
