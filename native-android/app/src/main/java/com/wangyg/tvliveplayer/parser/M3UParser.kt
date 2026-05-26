package com.wangyg.tvliveplayer.parser

import com.wangyg.tvliveplayer.domain.model.Channel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3UParser @Inject constructor() {

    fun parse(m3uContent: String, sourceCategory: String? = null): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = m3uContent.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                // Parse #EXTINF:-1 tvg-id="" tvg-logo="" group-title="分类",频道名称
                val tvgId = extractQuotedValue(line, "tvg-id")
                val logo = extractQuotedValue(line, "tvg-logo")
                val category = extractQuotedValue(line, "group-title") ?: sourceCategory ?: "未分类"
                val name = line.substringAfterLast(",").trim()
                // Next line should be the URL
                if (i + 1 < lines.size) {
                    i++
                    val url = lines[i].trim()
                    if (url.isNotEmpty() && !url.startsWith("#")) {
                        channels.add(Channel(
                            id = UUID.randomUUID().toString(),
                            name = name.ifEmpty { "未知频道" },
                            url = url,
                            category = category,
                            logo = logo?.ifEmpty { null },
                            tvgId = tvgId?.ifEmpty { null },
                            sortOrder = channels.size
                        ))
                    }
                }
            }
            i++
        }
        return channels
    }

    private fun extractQuotedValue(line: String, key: String): String? {
        val regex = Regex("""$key="([^"]*)"""")
        return regex.find(line)?.groupValues?.getOrNull(1)?.ifEmpty { null }
    }
}
