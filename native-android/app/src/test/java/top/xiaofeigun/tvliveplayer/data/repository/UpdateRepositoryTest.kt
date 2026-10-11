package top.xiaofeigun.tvliveplayer.data.repository

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.xiaofeigun.tvliveplayer.data.remote.model.GitHubReleaseDto
import top.xiaofeigun.tvliveplayer.util.VersionComparator

class UpdateRepositoryTest {

    private val gson = Gson()

    @Test
    fun testParseGitHubReleaseJson() {
        val json = """
            {
              "tag_name": "v2026.10.11",
              "name": "v2026.10.11",
              "body": "## Release Notes\n- Feature A\n- Fix B",
              "html_url": "https://github.com/xfgdck/TV-Live-Player/releases/tag/v2026.10.11",
              "assets": [
                {
                  "name": "TV-Live-Player-2026.10.11-release.apk",
                  "size": 11522402,
                  "browser_download_url": "https://github.com/xfgdck/TV-Live-Player/releases/download/v2026.10.11/TV-Live-Player-2026.10.11-release.apk"
                }
              ]
            }
        """.trimIndent()

        val dto = gson.fromJson(json, GitHubReleaseDto::class.java)
        assertNotNull(dto)
        assertEquals("v2026.10.11", dto.tagName)
        assertEquals("v2026.10.11", dto.name)
        assertEquals("## Release Notes\n- Feature A\n- Fix B", dto.body)
        assertEquals(1, dto.assets?.size)

        val asset = dto.assets?.firstOrNull()
        assertNotNull(asset)
        assertEquals("TV-Live-Player-2026.10.11-release.apk", asset?.name)
        assertEquals(11522402L, asset?.size)
        assertEquals(
            "https://github.com/xfgdck/TV-Live-Player/releases/download/v2026.10.11/TV-Live-Player-2026.10.11-release.apk",
            asset?.downloadUrl
        )

        // Version comparison check
        assertTrue(VersionComparator.isNewer("1.0.0", dto.tagName ?: ""))
        assertFalse(VersionComparator.isNewer("2026.10.11", dto.tagName ?: ""))
    }
}
