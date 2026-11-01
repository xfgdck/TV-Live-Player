package top.xiaofeigun.tvliveplayer.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.xiaofeigun.tvliveplayer.domain.model.StreamUrl

class M3UParserTest {

    private lateinit var parser: M3UParser

    @Before
    fun setup() {
        parser = M3UParser()
    }

    @Test
    fun testStandardM3uParsing() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1" tvg-logo="http://test.com/cctv1.png" group-title="央视频道",CCTV-1 综合
            http://example.com/cctv1.m3u8
        """.trimIndent()

        val channels = parser.parse(content)
        assertEquals(1, channels.size)
        val ch = channels[0]
        assertEquals("cctv1", ch.tvgId)
        assertEquals("http://test.com/cctv1.png", ch.logo)
        assertEquals("央视频道", ch.category)
        assertEquals("http://example.com/cctv1.m3u8", ch.url)
    }

    @Test
    fun testM3uWithBomAndNoHeader() {
        // UTF-8 BOM \uFEFF and no #EXTM3U header
        val content = "\uFEFF#EXTINF:-1 group-title='央视',CCTV-1\nhttp://example.com/cctv1.m3u8"

        val channels = parser.parse(content)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("央视", channels[0].category)
    }

    @Test
    fun testM3uAttributesVariations() {
        // Single quotes, unquoted attributes, uppercase group title
        val content = """
            #EXTINF:-1 GROUP-TITLE='卫视频道' tvg-logo="http://logo.png" tvg-id=hunan,湖南卫视
            http://example.com/hunan.m3u8
        """.trimIndent()

        val channels = parser.parse(content)
        assertEquals(1, channels.size)
        val ch = channels[0]
        assertEquals("卫视频道", ch.category)
        assertEquals("http://logo.png", ch.logo)
        assertEquals("hunan", ch.tvgId)
    }

    @Test
    fun testM3uExtGrpTag() {
        val content = """
            #EXTINF:-1,北京卫视
            #EXTGRP:卫视频道
            http://example.com/bjws.m3u8
        """.trimIndent()

        val channels = parser.parse(content)
        assertEquals(1, channels.size)
        assertEquals("卫视频道", channels[0].category)
    }

    @Test
    fun testM3uChannelNameWithCommas() {
        val content = """
            #EXTINF:-1 group-title="电影",Movie, The Action One
            http://example.com/movie.m3u8
        """.trimIndent()

        val channels = parser.parse(content)
        assertEquals(1, channels.size)
        assertEquals("Movie, The Action One", channels[0].name)
    }

    @Test
    fun testM3uUrlWithPipeParam() {
        val content = """
            #EXTINF:-1,CCTV-1
            http://example.com/cctv1.m3u8|User-Agent=okhttp
        """.trimIndent()

        val channels = parser.parse(content)
        assertEquals(1, channels.size)
        assertEquals("http://example.com/cctv1.m3u8|User-Agent=okhttp", channels[0].url)
    }

    @Test
    fun testM3uRequestHeaders() {
        val content = """
            #EXTM3U http-user-agent="GlobalUA"
            #EXTINF:-1 http-referrer="https://a.com/",频道A
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 (Linux; Android 10)
            #EXTVLCOPT:network-caching=1000
            http://example.com/a.m3u8
            #EXTINF:-1,频道B
            #EXTHTTP:{"Referer":"https://b.com/","Origin":"https://b.com"}
            http://example.com/b.m3u8|User-Agent=Own%20UA
            #EXTINF:-1,频道C
            http://example.com/c.m3u8
        """.trimIndent()
        val byName = parser.parse(content).associateBy { it.name }

        val a = StreamUrl.parse(byName.getValue("频道A").url)
        assertEquals("http://example.com/a.m3u8", a.url)
        assertEquals(
            mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 10)", "Referer" to "https://a.com/"),
            a.headers
        )

        // 地址自带的请求头优先于条目与播放列表级请求头
        val b = StreamUrl.parse(byName.getValue("频道B").url)
        assertEquals(
            mapOf("User-Agent" to "Own UA", "Referer" to "https://b.com/", "Origin" to "https://b.com"),
            b.headers
        )

        assertEquals(mapOf("User-Agent" to "GlobalUA"), StreamUrl.parse(byName.getValue("频道C").url).headers)
    }

    @Test
    fun testInvalidHeadersAreDropped() {
        val content = """
            #EXTINF:-1 http-referrer="https://例子.com/",频道A
            #EXTHTTP:{"Bad Name":"x","Origin":"https://ok.com"}
            http://example.com/a.m3u8|User-Agent=中文UA
        """.trimIndent()
        val stream = StreamUrl.parse(parser.parse(content).single().url)
        assertEquals("http://example.com/a.m3u8", stream.url)
        assertEquals(mapOf("Origin" to "https://ok.com"), stream.headers)
        // 播放时解析旧数据同样过滤
        assertTrue(StreamUrl.parse("http://x|Referer=https://例子.com/").headers.isEmpty())
    }

    @Test
    fun testStreamUrlRoundTrip() {
        val stream = StreamUrl("http://x.com/live.m3u8", mapOf("User-Agent" to "a&b|c 100%+", "Referer" to "http://r/"))
        assertEquals(stream, StreamUrl.parse(stream.encode()))
        assertEquals("http://x.com/live.m3u8", StreamUrl("http://x.com/live.m3u8").encode())
    }

    @Test
    fun testTxtStandardWithGenre() {
        val content = """
            央视频道,#genre#
            CCTV-1,http://example.com/cctv1.m3u8
            CCTV-2,http://example.com/cctv2.m3u8
            卫视频道,#genre#
            浙江卫视,http://example.com/zjws.m3u8
        """.trimIndent()

        val channels = parser.parse(content)
        assertEquals(3, channels.size)
        assertEquals("央视频道", channels[0].category)
        assertEquals("央视频道", channels[1].category)
        assertEquals("卫视频道", channels[2].category)
    }

    @Test
    fun testTxtChineseCommaAndMultiSourceHash() {
        val content = """
            央视频道，#genre#
            CCTV-1，http://example.com/cctv1_1.m3u8#http://example.com/cctv1_2.m3u8#http://example.com/cctv1_3.m3u8
        """.trimIndent()

        val channels = parser.parse(content)
        assertEquals(1, channels.size)
        val ch = channels[0]
        assertEquals("http://example.com/cctv1_1.m3u8", ch.url)
        assertEquals(2, ch.backupUrls.size)
        assertEquals("http://example.com/cctv1_2.m3u8", ch.backupUrls[0])
        assertEquals("http://example.com/cctv1_3.m3u8", ch.backupUrls[1])
    }

    @Test
    fun testTxtBracketedCategories() {
        val content = """
            [体育频道]
            CCTV-5,http://example.com/cctv5.m3u8
            【少儿频道】
            少儿,http://example.com/cctv14.m3u8
        """.trimIndent()

        val channels = parser.parse(content)
        assertEquals(2, channels.size)
        assertEquals("体育频道", channels[0].category)
        assertEquals("少儿频道", channels[1].category)
    }

    @Test
    fun testTxtSeparatorsTabAndSpace() {
        val content = """
            CCTV-1	http://example.com/cctv1.m3u8
            CCTV-2 http://example.com/cctv2.m3u8
            CCTV-3;http://example.com/cctv3.m3u8
        """.trimIndent()

        val channels = parser.parse(content)
        assertEquals(3, channels.size)
        assertEquals("http://example.com/cctv1.m3u8", channels[0].url)
        assertEquals("http://example.com/cctv2.m3u8", channels[1].url)
        assertEquals("http://example.com/cctv3.m3u8", channels[2].url)
    }

    @Test
    fun testIntelligentDeduplicationAndBackupUrlsMerging() {
        // Same channel written in different formats within same document
        val content = """
            央视频道,#genre#
            CCTV-1 高清,http://host1/cctv1.m3u8
            CCTV1 综合 (1080P),http://host2/cctv1.m3u8
            【HD】CCTV-1,http://host3/cctv1.m3u8
            CCTV-1,http://host1/cctv1.m3u8
        """.trimIndent()

        val channels = parser.parse(content)
        // Should be merged into exactly 1 channel
        assertEquals(1, channels.size)
        val ch = channels[0]
        assertEquals("http://host1/cctv1.m3u8", ch.url)
        assertEquals(2, ch.backupUrls.size)
        assertTrue(ch.backupUrls.contains("http://host2/cctv1.m3u8"))
        assertTrue(ch.backupUrls.contains("http://host3/cctv1.m3u8"))
    }

    @Test
    fun testNormalizationKey() {
        assertEquals("cctv1", M3UParser.normalizeKey("CCTV-1"))
        assertEquals("cctv1", M3UParser.normalizeKey("CCTV1"))
        assertEquals("cctv1", M3UParser.normalizeKey("CCTV-1 高清"))
        assertEquals("cctv1", M3UParser.normalizeKey("CCTV1 综合 (1080P)"))
        assertEquals("cctv1", M3UParser.normalizeKey("【4K】CCTV-1"))
        assertEquals("cctv5+", M3UParser.normalizeKey("CCTV-5+ 体育赛事"))
        assertEquals("cctv13", M3UParser.normalizeKey("CCTV-13 新闻"))
        assertEquals("湖南卫视", M3UParser.normalizeKey("湖南卫视 高清"))
        assertEquals("湖南卫视", M3UParser.normalizeKey("【超清】湖南卫视"))
        assertEquals("东方卫视", M3UParser.normalizeKey("东方卫视 (1080P)"))
        assertEquals("湖南卫视", M3UParser.normalizeKey("湖南卫视 综合"))
    }

    @Test
    fun testDistinctChannelsAreNotMerged() {
        assertEquals("凤凰卫视中文台", M3UParser.normalizeKey("凤凰卫视中文台"))
        assertEquals("凤凰卫视资讯台", M3UParser.normalizeKey("凤凰卫视资讯台 HD"))
        assertEquals("cctv4欧洲", M3UParser.normalizeKey("CCTV-4 欧洲"))
        assertEquals("cctv4美洲", M3UParser.normalizeKey("CCTV4美洲"))
        assertEquals("cctv4", M3UParser.normalizeKey("CCTV-4 中文国际"))

        val content = """
            #EXTM3U
            #EXTINF:-1,凤凰卫视中文台
            http://example.com/zw.m3u8
            #EXTINF:-1,凤凰卫视资讯台
            http://example.com/zx.m3u8
        """.trimIndent()
        assertEquals(2, parser.parse(content).size)
    }

    @Test
    fun testCommaInsideQuotedAttribute() {
        val content = """
            #EXTM3U
            #EXTINF:-1 group-title="新闻,资讯" tvg-name="CCTV-13",CCTV-13 新闻
            http://example.com/cctv13.m3u8
        """.trimIndent()
        val ch = parser.parse(content).single()
        assertEquals("CCTV-13 新闻", ch.name)
        assertEquals("新闻,资讯", ch.category)
    }

    @Test
    fun testUnsupportedProtocolsAreDropped() {
        val content = """
            #EXTM3U
            #EXTINF:-1,CCTV-1
            rtmp://example.com/live/cctv1
            rtsp://example.com/cctv1
            udp://239.0.0.1:5000
            #EXTINF:-1,P2P 频道
            p2p://example.com/abc
            mitv://example.com/def
        """.trimIndent()
        val ch = parser.parse(content).single()
        assertEquals("rtsp://example.com/cctv1", ch.url)
        assertEquals(listOf("udp://239.0.0.1:5000"), ch.backupUrls)
    }

    @Test
    fun testTxtUnsupportedProtocolsAreDropped() {
        val content = "央视,#genre#\nCCTV-1,rtp://239.0.0.1:5000#http://example.com/cctv1.m3u8\nCCTV-2,mms://example.com/cctv2"
        val ch = parser.parse(content).single()
        assertEquals("http://example.com/cctv1.m3u8", ch.url)
        assertTrue(ch.backupUrls.isEmpty())
    }

    @Test
    fun testIsPlayableUrl() {
        assertTrue(StreamUrl.isPlayable("HTTPS://example.com/a.m3u8"))
        assertTrue(StreamUrl.isPlayable("udp://239.0.0.1:5000"))
        assertTrue(!StreamUrl.isPlayable("rtmp://example.com/live"))
        assertTrue(!StreamUrl.isPlayable("rtp://239.0.0.1:5000"))
    }
}
