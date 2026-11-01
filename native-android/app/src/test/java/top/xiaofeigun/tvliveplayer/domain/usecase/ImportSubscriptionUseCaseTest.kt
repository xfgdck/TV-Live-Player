package top.xiaofeigun.tvliveplayer.domain.usecase

import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.xiaofeigun.tvliveplayer.data.remote.PlaylistFetcher
import top.xiaofeigun.tvliveplayer.data.repository.ChannelRepositoryImpl
import top.xiaofeigun.tvliveplayer.domain.model.Source
import top.xiaofeigun.tvliveplayer.parser.M3UParser
import top.xiaofeigun.tvliveplayer.testutil.FakeChannelDao
import top.xiaofeigun.tvliveplayer.testutil.FakeSourceDao
import top.xiaofeigun.tvliveplayer.testutil.ImmediateTransactionRunner
import java.io.IOException

class ImportSubscriptionUseCaseTest {

    /** url → 返回内容；值为 null 时模拟网络失败 */
    private val responses = mutableMapOf<String, String?>()
    /** 模拟下载期间用户的操作（重命名、删除） */
    private var duringFetch: suspend () -> Unit = {}
    private lateinit var repository: ChannelRepositoryImpl
    private lateinit var useCase: ImportSubscriptionUseCase

    @Before
    fun setup() {
        repository = ChannelRepositoryImpl(FakeChannelDao(), FakeSourceDao(), Gson(), ImmediateTransactionRunner)
        val fetcher = object : PlaylistFetcher(OkHttpClient()) {
            override suspend fun fetch(url: String): String {
                duringFetch()
                return responses[url] ?: throw IOException("HTTP 404")
            }
        }
        useCase = ImportSubscriptionUseCase(fetcher, ParseAndImportM3UUseCase(M3UParser(), repository), repository)
    }

    private suspend fun source(id: String) = repository.getAllSources().first().first { it.id == id }

    @Test
    fun testImportRecordsStatusAndKeepsCustomName() = runBlocking {
        responses["http://a.com/list.txt"] = "CCTV-1,http://x/1\nCCTV-2,http://x/2"
        assertEquals(2, useCase("http://a.com/list.txt"))
        val first = source("http://a.com/list.txt")
        assertEquals("a.com", first.name)
        assertEquals(2, first.channelCount)
        assertTrue(first.lastUpdated > 0)

        // 重命名后再次导入同一地址：保留名称与创建时间
        repository.addSource(first.copy(name = "我的源"))
        responses["http://a.com/list.txt"] = "CCTV-1,http://x/1"
        useCase("http://a.com/list.txt")
        val again = source("http://a.com/list.txt")
        assertEquals("我的源", again.name)
        assertEquals(first.createdAt, again.createdAt)
        assertEquals(1, again.channelCount)
    }

    @Test
    fun testRefreshFailureRecordsErrorAndKeepsLastSuccess() = runBlocking {
        responses["http://a"] = "CCTV-1,http://x/1"
        useCase("http://a")
        val ok = source("http://a")

        responses["http://a"] = null
        assertTrue(runCatching { useCase.refresh(ok) }.isFailure)
        val failed = source("http://a")
        assertEquals("HTTP 404", failed.lastError)
        assertEquals(ok.lastUpdated, failed.lastUpdated)

        // 空内容同样视为失败，且不会清空已有频道
        responses["http://a"] = "#EXTM3U"
        assertTrue(runCatching { useCase.refresh(failed) }.isFailure)
        assertEquals("未解析到任何频道", source("http://a").lastError)
        assertEquals(1, repository.getAllChannels().first().size)

        responses["http://a"] = "CCTV-1,http://x/1"
        useCase.refresh(source("http://a"))
        assertNull(source("http://a").lastError)
    }

    @Test
    fun testRefreshKeepsRenameAndDoesNotResurrectDeletedSource() = runBlocking {
        responses["http://a"] = "CCTV-1,http://x/1"
        useCase("http://a")

        duringFetch = { repository.addSource(source("http://a").copy(name = "新名字")) }
        useCase.refresh(source("http://a"))
        assertEquals("新名字", source("http://a").name)

        // 更新期间删除：成功时不写回并清理本次导入的地址
        duringFetch = { repository.deleteSource("http://a") }
        useCase.refresh(Source(id = "http://a", name = "a", url = "http://a"))
        assertTrue(repository.getAllSources().first().isEmpty())
        assertTrue(repository.getAllChannels().first().isEmpty())

        // 失败时同样不会因写入失败原因而复活
        repository.addSource(Source(id = "http://b", name = "b", url = "http://b"))
        duringFetch = { repository.deleteSource("http://b") }
        assertTrue(runCatching { useCase.refresh(Source(id = "http://b", name = "b", url = "http://b")) }.isFailure)
        assertTrue(repository.getAllSources().first().isEmpty())
    }

    @Test
    fun testRefreshStaleOnlyRefreshesExpiredSources() = runBlocking {
        val now = 100 * ImportSubscriptionUseCase.AUTO_REFRESH_INTERVAL_MS
        repository.addSource(Source(id = "fresh", name = "fresh", url = "fresh", lastUpdated = now - 1000))
        repository.addSource(Source(id = "stale", name = "stale", url = "stale", lastUpdated = 0))
        responses["fresh"] = "A,http://x/a"
        responses["stale"] = "B,http://x/b"

        val result = useCase.refreshStale(now)

        assertEquals(1, result.succeeded)
        assertEquals(listOf("B"), repository.getAllChannels().first().map { it.name })
    }
}
