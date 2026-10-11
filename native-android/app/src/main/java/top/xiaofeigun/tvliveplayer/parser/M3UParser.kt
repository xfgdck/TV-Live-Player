package top.xiaofeigun.tvliveplayer.parser

import top.xiaofeigun.tvliveplayer.domain.model.Channel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3UParser @Inject constructor() {

    fun parse(m3uContent: String, sourceCategory: String? = null): List<Channel> {
        val cleaned = cleanContent(m3uContent)
        if (cleaned.isBlank()) return emptyList()

        val rawChannels = if (isM3uContent(cleaned)) {
            parseM3u(cleaned, sourceCategory)
        } else {
            parseTxt(cleaned, sourceCategory)
        }
        return mergeByName(rawChannels)
    }

    private fun cleanContent(content: String): String {
        return content
            .removePrefix("\uFEFF") // UTF-8 BOM
            .removePrefix("\uEFBBBF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("""[\u200B-\u200D\uFE0E\uFE0F\u00A0]"""), "")
    }

    private fun isM3uContent(content: String): Boolean {
        val sample = content.take(2048)
        return sample.contains("#EXTM3U", ignoreCase = true) ||
                sample.contains("#EXTINF", ignoreCase = true)
    }

    private fun parseM3u(content: String, sourceCategory: String? = null): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val attrs = parseAttributes(line)
                var category = attrs["group-title"] ?: attrs["group"] ?: attrs["tvg-group"]
                val logo = attrs["tvg-logo"] ?: attrs["logo"]
                val tvgId = attrs["tvg-id"]
                val tvgName = attrs["tvg-name"]

                // Channel name is after the first comma
                val commaIdx = line.indexOf(',')
                var name = if (commaIdx != -1 && commaIdx + 1 < line.length) {
                    line.substring(commaIdx + 1).trim()
                } else {
                    ""
                }
                if (name.isEmpty()) {
                    name = tvgName?.trim() ?: tvgId?.trim() ?: "未知频道"
                }

                // Check following lines for #EXTGRP and URLs
                val channelUrls = mutableListOf<String>()
                while (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (nextLine.startsWith("#EXTINF:", ignoreCase = true)) {
                        break
                    }
                    i++
                    if (nextLine.isEmpty()) continue

                    if (nextLine.startsWith("#EXTGRP:", ignoreCase = true)) {
                        if (category.isNullOrBlank()) {
                            category = nextLine.substringAfter(":").trim()
                        }
                        continue
                    }

                    if (nextLine.startsWith("#")) {
                        // Skip other comment/directive lines
                        continue
                    }

                    // Found URL line
                    val cleanedUrl = cleanUrl(nextLine)
                    if (cleanedUrl.isNotEmpty()) {
                        channelUrls.add(cleanedUrl)
                    }
                }

                val finalCategory = normalizeCategory(category ?: sourceCategory ?: "未分类")

                if (channelUrls.isNotEmpty()) {
                    channels.add(
                        Channel(
                            id = UUID.randomUUID().toString(),
                            name = cleanChannelName(name),
                            url = channelUrls.first(),
                            backupUrls = channelUrls.drop(1),
                            category = finalCategory,
                            logo = logo?.ifEmpty { null },
                            tvgId = tvgId?.ifEmpty { null },
                            sortOrder = channels.size
                        )
                    )
                }
            }
            i++
        }
        return channels
    }

    private fun parseTxt(content: String, sourceCategory: String? = null): List<Channel> {
        val channels = mutableListOf<Channel>()
        var currentCategory = normalizeCategory(sourceCategory ?: "未分类")

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 1. Detect category headers
            val genreIdx = trimmed.indexOf("#genre#", ignoreCase = true)
            if (genreIdx >= 0) {
                val cat = trimmed.substring(0, genreIdx)
                    .substringBefore(",")
                    .substringBefore("，")
                    .trim()
                if (cat.isNotEmpty()) {
                    currentCategory = normalizeCategory(cat)
                }
                continue
            }

            val bracketCat = when {
                trimmed.startsWith("[") && trimmed.endsWith("]") -> trimmed.removeSurrounding("[", "]").trim()
                trimmed.startsWith("【") && trimmed.endsWith("】") -> trimmed.removeSurrounding("【", "】").trim()
                else -> null
            }
            if (bracketCat != null && !bracketCat.contains("://") && !bracketCat.contains("http", ignoreCase = true)) {
                if (bracketCat.isNotEmpty()) {
                    currentCategory = normalizeCategory(bracketCat)
                }
                continue
            }

            if (trimmed.startsWith("#") && !trimmed.contains(",") && !trimmed.contains("，") && !trimmed.contains("://")) {
                val cat = trimmed.trimStart('#').trim()
                if (cat.isNotEmpty()) {
                    currentCategory = normalizeCategory(cat)
                }
                continue
            }

            if ((trimmed.endsWith(":") || trimmed.endsWith("：")) && !trimmed.contains("://")) {
                val cat = trimmed.dropLast(1).trim()
                if (cat.isNotEmpty()) {
                    currentCategory = normalizeCategory(cat)
                }
                continue
            }

            // Skip pure comment lines
            if (trimmed.startsWith("#")) continue

            // 2. Extract channel name and URL part
            val (name, urlPart) = extractTxtNameAndUrl(trimmed) ?: continue
            if (name.isEmpty() || urlPart.isEmpty()) continue

            // 3. Handle multi-source concatenated with '#' or '$'
            val urls = urlPart.split(Regex("""[#$]"""))
                .map { cleanUrl(it) }
                .filter { it.isNotEmpty() }

            if (urls.isNotEmpty()) {
                channels.add(
                    Channel(
                        id = UUID.randomUUID().toString(),
                        name = cleanChannelName(name),
                        url = urls.first(),
                        backupUrls = urls.drop(1),
                        category = currentCategory,
                        sortOrder = channels.size
                    )
                )
            }
        }
        return channels
    }

    private fun extractTxtNameAndUrl(line: String): Pair<String, String>? {
        // Try common delimiters
        val delimiters = listOf(",", "，", "\t", ";", "；")
        for (delimiter in delimiters) {
            val idx = line.indexOf(delimiter)
            if (idx > 0 && idx + 1 < line.length) {
                return Pair(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
            }
        }

        // Space delimiter with scheme detection
        val schemes = listOf("http://", "https://", "rtmp://", "rtsp://", "udp://", "rtp://", "p2p://", "mms://", "mitv://")
        for (scheme in schemes) {
            val schemeIdx = line.indexOf(scheme, ignoreCase = true)
            if (schemeIdx > 0) {
                return Pair(line.substring(0, schemeIdx).trim(), line.substring(schemeIdx).trim())
            }
        }

        return null
    }

    private fun parseAttributes(line: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val regex = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"',]+))""")
        val matches = regex.findAll(line)
        for (match in matches) {
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].ifEmpty {
                match.groupValues[3].ifEmpty {
                    match.groupValues[4]
                }
            }
            if (value.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    private fun cleanUrl(raw: String): String {
        var url = raw.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()
        // Handle pipe parameters: http://host/live.m3u8|User-Agent=...
        if (url.contains("|")) {
            val beforePipe = url.substringBefore("|").trim()
            if (isValidProtocol(beforePipe)) {
                url = beforePipe
            }
        }
        return if (isValidProtocol(url)) url else ""
    }

    private fun isValidProtocol(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("rtmp://") ||
                lower.startsWith("rtsp://") ||
                lower.startsWith("udp://") ||
                lower.startsWith("rtp://") ||
                lower.startsWith("p2p://") ||
                lower.startsWith("mms://") ||
                lower.startsWith("mitv://")
    }

    private fun cleanChannelName(raw: String): String {
        return raw.trim()
            .removeSurrounding("\"", "\"")
            .removeSurrounding("'", "'")
            .trim()
    }

    private fun mergeByName(channels: List<Channel>): List<Channel> {
        val groups = linkedMapOf<String, MutableList<Channel>>()
        for (ch in channels) {
            val key = normalizeKey(ch.name)
            groups.getOrPut(key) { mutableListOf() }.add(ch)
        }

        return groups.map { (_, list) ->
            val first = list.first()
            val allUrls = list.flatMap { listOf(it.url) + it.backupUrls }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            // Select best metadata
            val bestName = list.map { it.name }.firstOrNull { it != "未知频道" && !it.startsWith("http") } ?: first.name
            val bestLogo = list.firstNotNullOfOrNull { it.logo?.ifBlank { null } }
            val bestTvgId = list.firstNotNullOfOrNull { it.tvgId?.ifBlank { null } }
            val bestCategory = list.map { it.category }.firstOrNull { it != "未分类" } ?: first.category

            first.copy(
                id = UUID.randomUUID().toString(),
                name = bestName,
                url = allUrls.firstOrNull() ?: first.url,
                backupUrls = allUrls.drop(1),
                category = bestCategory,
                logo = bestLogo,
                tvgId = bestTvgId
            )
        }
    }

    companion object {
        /**
         * Cleans category syntax wrappers (brackets, quotes, spaces) while 100% respecting
         * and preserving the original category name specified in the source.
         */
        fun normalizeCategory(category: String): String {
            val trimmed = category.trim()
                .removeSurrounding("[", "]")
                .removeSurrounding("【", "】")
                .removeSurrounding("\"", "\"")
                .removeSurrounding("'", "'")
                .trim()
            return if (trimmed.isEmpty()) "未分类" else trimmed
        }

        /**
         * Normalizes channel name to a canonical comparison key for intelligent deduplication.
         * Example:
         * "CCTV-1 高清" -> "cctv1"
         * "CCTV1 综合 (1080P)" -> "cctv1"
         * "【4K】湖南卫视" -> "湖南卫视"
         */
        fun normalizeKey(name: String): String {
            if (name.isBlank()) return ""
            var clean = name.lowercase().trim()

            // 1. Remove bracketed quality tags like [高清], (1080P), 【HD】, [4K], etc.
            val bracketTagRegex = Regex("""[\(\[\{（【][^)\]}）】]*(hd|fhd|uhd|4k|8k|1080p|720p|高清|超清|标清|蓝光|ipv6|ipv4|hevc|h265|h264|测试|备用|专线|移动|联通|电信|联通)[^)\]}）】]*[\)\]\}）】]""", RegexOption.IGNORE_CASE)
            clean = clean.replace(bracketTagRegex, " ")

            // Remove any leftover brackets
            clean = clean.replace(Regex("""[\(\)\[\]{}（）【】]"""), " ")

            // 2. Strip standalone quality keywords
            val qualityKeywords = listOf(
                "1080p", "720p", "4k", "8k", "fhd", "uhd", "hd", "sd",
                "hevc", "h265", "h264", "ipv6", "ipv4",
                "超高清", "高清", "超清", "标清", "蓝光", "测试", "备用", "专线"
            )
            for (kw in qualityKeywords) {
                clean = clean.replace(kw, " ")
            }

            // 3. Normalize CCTV naming
            // E.g. "cctv-1", "cctv 1", "cctv1" -> "cctv1"
            val cctvNumRegex = Regex("""cctv\s*[-_]?\s*(\d+)(\+?)""")
            val cctvNumMatch = cctvNumRegex.find(clean)
            if (cctvNumMatch != null) {
                val num = cctvNumMatch.groupValues[1]
                val plus = cctvNumMatch.groupValues[2]
                return "cctv$num$plus"
            }

            // Named CCTV channels mapping
            if (clean.contains("cctv")) {
                when {
                    clean.contains("综合") -> return "cctv1"
                    clean.contains("财经") -> return "cctv2"
                    clean.contains("综艺") -> return "cctv3"
                    clean.contains("中文国际") || clean.contains("国际") -> return "cctv4"
                    clean.contains("体育赛事") || clean.contains("5+") -> return "cctv5+"
                    clean.contains("体育") -> return "cctv5"
                    clean.contains("电影") -> return "cctv6"
                    clean.contains("国防军事") || clean.contains("军事") -> return "cctv7"
                    clean.contains("电视剧") -> return "cctv8"
                    clean.contains("纪录") -> return "cctv9"
                    clean.contains("科教") -> return "cctv10"
                    clean.contains("戏曲") -> return "cctv11"
                    clean.contains("社会与法") -> return "cctv12"
                    clean.contains("新闻") -> return "cctv13"
                    clean.contains("少儿") -> return "cctv14"
                    clean.contains("音乐") -> return "cctv15"
                    clean.contains("奥林匹克") || clean.contains("奥运") -> return "cctv16"
                    clean.contains("农业农村") || clean.contains("农业") -> return "cctv17"
                }
            }

            // 4. Satellite TV channels: "湖南卫视 综合" -> "湖南卫视"
            val satelliteMatch = Regex("""(\S+卫视)""").find(clean)
            if (satelliteMatch != null) {
                return satelliteMatch.groupValues[1].replace(Regex("""[\s\-_]"""), "")
            }

            // 5. Remove all remaining spaces, hyphens, and underscores
            clean = clean.replace(Regex("""[\s\-_.:/，,]+"""), "")
            return clean.ifEmpty { name.trim().lowercase() }
        }
    }
}
