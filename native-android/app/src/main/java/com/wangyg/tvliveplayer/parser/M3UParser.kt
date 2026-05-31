package com.wangyg.tvliveplayer.parser

import com.wangyg.tvliveplayer.domain.model.Channel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3UParser @Inject constructor() {

    fun parse(m3uContent: String, sourceCategory: String? = null): List<Channel> {
        val trimmed = m3uContent.trim()
        val rawChannels = if (trimmed.startsWith("#EXTM3U", ignoreCase = true)) {
            parseM3u(trimmed, sourceCategory)
        } else {
            parseTxt(trimmed, sourceCategory)
        }
        return mergeByName(rawChannels)
    }

    private fun mergeByName(channels: List<Channel>): List<Channel> {
        val groups = linkedMapOf<String, MutableList<Channel>>()
        for (ch in channels) {
            val key = ch.name.trim().lowercase()
            groups.getOrPut(key) { mutableListOf() }.add(ch)
        }
        return groups.map { (_, list) ->
            val first = list.first()
            val allUrls = list.flatMap { listOf(it.url) + it.backupUrls }.distinct()
            first.copy(
                id = UUID.randomUUID().toString(),
                url = allUrls.first(),
                backupUrls = allUrls.drop(1)
            )
        }
    }

    private fun parseM3u(content: String, sourceCategory: String? = null): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                val tvgId = extractQuotedValue(line, "tvg-id")
                val logo = extractQuotedValue(line, "tvg-logo")
                val category = extractQuotedValue(line, "group-title") ?: sourceCategory ?: "未分类"
                val name = line.substringAfterLast(",").trim()
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

    private fun parseTxt(content: String, sourceCategory: String? = null): List<Channel> {
        val channels = mutableListOf<Channel>()
        var currentCategory = sourceCategory ?: "未分类"

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.endsWith("#genre#", ignoreCase = true)) {
                val cat = trimmed.substringBeforeLast(",").trim()
                if (cat.isNotEmpty()) currentCategory = cat
                continue
            }

            if (trimmed.startsWith("#")) continue

            val commaIdx = trimmed.indexOf(",")
            if (commaIdx <= 0) continue

            val name = trimmed.substring(0, commaIdx).trim()
            val url = trimmed.substring(commaIdx + 1).trim()

            if (name.isEmpty() || url.isEmpty()) continue

            channels.add(Channel(
                id = UUID.randomUUID().toString(),
                name = name,
                url = url,
                category = currentCategory,
                sortOrder = channels.size
            ))
        }
        return channels
    }

    private fun extractQuotedValue(line: String, key: String): String? {
        val regex = Regex("""$key="([^"]*)"""")
        return regex.find(line)?.groupValues?.getOrNull(1)?.ifEmpty { null }
    }
}
