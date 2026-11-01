package top.xiaofeigun.tvliveplayer.parser

import com.google.gson.JsonParser
import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.model.StreamUrl
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
        // #EXTM3U 行上的 http-user-agent / http-referrer 作为全部频道的默认请求头
        val playlistHeaders = lines.firstOrNull { it.trim().startsWith("#EXTM3U", ignoreCase = true) }
            ?.let { headerAttributes(parseAttributes(it)) }
            .orEmpty()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val attrs = parseAttributes(line)
                var category = attrs["group-title"] ?: attrs["group"] ?: attrs["tvg-group"]
                val logo = attrs["tvg-logo"] ?: attrs["logo"]
                val tvgId = attrs["tvg-id"]
                val tvgName = attrs["tvg-name"]
                val entryHeaders = LinkedHashMap(playlistHeaders).apply { putAll(headerAttributes(attrs)) }

                // Channel name is after the first comma outside quoted attribute values
                val commaIdx = indexOfUnquotedComma(line)
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

                    if (nextLine.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                        parseVlcOpt(nextLine)?.let { (k, v) -> entryHeaders[k] = v }
                        continue
                    }

                    if (nextLine.startsWith("#EXTHTTP:", ignoreCase = true)) {
                        entryHeaders.putAll(parseExtHttp(nextLine))
                        continue
                    }

                    if (nextLine.startsWith("#")) {
                        // Skip other comment/directive lines
                        continue
                    }

                    // Found URL line（请求头指令可能出现在 URL 之后，统一在条目结束后附加）
                    channelUrls.add(nextLine)
                }
                val cleanedUrls = channelUrls.map { cleanUrl(it, entryHeaders) }.filter { it.isNotEmpty() }

                val finalCategory = normalizeCategory(category ?: sourceCategory ?: "未分类")

                if (cleanedUrls.isNotEmpty()) {
                    channels.add(
                        Channel(
                            id = UUID.randomUUID().toString(),
                            name = cleanChannelName(name),
                            url = cleanedUrls.first(),
                            backupUrls = cleanedUrls.drop(1),
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

    private fun indexOfUnquotedComma(line: String): Int {
        var quote: Char? = null
        line.forEachIndexed { i, c ->
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == ',' -> return i
            }
        }
        // 引号未闭合（不规范的源）时退回首个逗号
        return line.indexOf(',')
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

    /**
     * 规范化播放地址：保留 `url|User-Agent=...` 形式的请求头，并合并条目级请求头（地址自带的优先）。
     * 播放器不支持的协议（rtmp / rtp / p2p / mms / mitv 等）直接丢弃，避免备用源轮换时白等。
     */
    private fun cleanUrl(raw: String, headers: Map<String, String> = emptyMap()): String {
        val stream = StreamUrl.parse(raw.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'"))
        if (!StreamUrl.isPlayable(stream.url)) return ""
        val merged = (headers + stream.headers).filter { (k, v) -> StreamUrl.isValidHeader(k, v) }
        return stream.copy(headers = merged).encode()
    }

    private fun headerAttributes(attrs: Map<String, String>): Map<String, String> =
        attrs.filterKeys { it in HEADER_ATTRIBUTES }.mapKeys { StreamUrl.headerName(it.key) }

    /** `#EXTVLCOPT:http-user-agent=xxx` / `#EXTVLCOPT:http-referrer=xxx`，其余 VLC 选项忽略 */
    private fun parseVlcOpt(line: String): Pair<String, String>? {
        val opt = line.substringAfter(":")
        val name = StreamUrl.headerName(opt.substringBefore("="))
        val value = opt.substringAfter("=", "").trim().removeSurrounding("\"", "\"")
        if (value.isEmpty() || (name != StreamUrl.USER_AGENT && name != StreamUrl.REFERER)) return null
        return name to value
    }

    /** `#EXTHTTP:{"User-Agent":"xxx","Referer":"yyy"}` */
    private fun parseExtHttp(line: String): Map<String, String> = try {
        JsonParser.parseString(line.substringAfter(":")).asJsonObject.entrySet()
            .filter { it.value.isJsonPrimitive }
            .associate { StreamUrl.headerName(it.key) to it.value.asString }
            .filter { it.key.isNotEmpty() && it.value.isNotBlank() }
    } catch (_: Exception) {
        emptyMap()
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
        private val HEADER_ATTRIBUTES = setOf(
            "http-user-agent", "user-agent", "http-referrer", "http-referer", "referrer", "referer"
        )

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
            clean = clean.replace(BRACKET_TAG_REGEX, " ")

            // Remove any leftover brackets
            clean = clean.replace(BRACKET_REGEX, " ")

            // 2. Strip standalone quality keywords
            for (kw in QUALITY_KEYWORDS) {
                clean = clean.replace(kw, " ")
            }

            // 3. Normalize CCTV naming
            // E.g. "cctv-1", "cctv 1", "cctv1" -> "cctv1"; 区域版本单独保留: "CCTV4 欧洲" -> "cctv4欧洲"
            val region = CCTV_REGIONS.firstOrNull { clean.contains(it) }.orEmpty()
            val cctvNumMatch = CCTV_NUM_REGEX.find(clean)
            if (cctvNumMatch != null) {
                val num = cctvNumMatch.groupValues[1]
                val plus = cctvNumMatch.groupValues[2]
                return "cctv$num$plus$region"
            }

            // Named CCTV channels mapping
            if (clean.contains("cctv")) {
                val mapped = when {
                    clean.contains("综合") -> "cctv1"
                    clean.contains("财经") -> "cctv2"
                    clean.contains("综艺") -> "cctv3"
                    clean.contains("中文国际") || clean.contains("国际") -> "cctv4"
                    clean.contains("体育赛事") || clean.contains("5+") -> "cctv5+"
                    clean.contains("体育") -> "cctv5"
                    clean.contains("电影") -> "cctv6"
                    clean.contains("国防军事") || clean.contains("军事") -> "cctv7"
                    clean.contains("电视剧") -> "cctv8"
                    clean.contains("纪录") -> "cctv9"
                    clean.contains("科教") -> "cctv10"
                    clean.contains("戏曲") -> "cctv11"
                    clean.contains("社会与法") -> "cctv12"
                    clean.contains("新闻") -> "cctv13"
                    clean.contains("少儿") -> "cctv14"
                    clean.contains("音乐") -> "cctv15"
                    clean.contains("奥林匹克") || clean.contains("奥运") -> "cctv16"
                    clean.contains("农业农村") || clean.contains("农业") -> "cctv17"
                    else -> null
                }
                if (mapped != null) return mapped + region
            }

            // 4. Satellite TV channels: "湖南卫视 综合" -> "湖南卫视"，但 "凤凰卫视中文台" 与 "凤凰卫视资讯台" 保持区分
            val satelliteMatch = SATELLITE_REGEX.find(clean)
            if (satelliteMatch != null) {
                val base = satelliteMatch.groupValues[1].replace(SEPARATOR_REGEX, "")
                val suffix = clean.substring(satelliteMatch.range.last + 1).replace(SEPARATOR_REGEX, "")
                return if (suffix in SATELLITE_GENERIC_SUFFIXES) base else base + suffix
            }

            // 5. Remove all remaining spaces, hyphens, and underscores
            clean = clean.replace(SEPARATOR_REGEX, "")
            return clean.ifEmpty { name.trim().lowercase() }
        }

        private val BRACKET_TAG_REGEX = Regex("""[\(\[\{（【][^)\]}）】]*(hd|fhd|uhd|4k|8k|1080p|720p|高清|超清|标清|蓝光|ipv6|ipv4|hevc|h265|h264|测试|备用|专线|移动|联通|电信)[^)\]}）】]*[\)\]\}）】]""", RegexOption.IGNORE_CASE)
        private val BRACKET_REGEX = Regex("""[\(\)\[\]{}（）【】]""")
        private val CCTV_NUM_REGEX = Regex("""cctv\s*[-_]?\s*(\d+)(\+?)""")
        private val SATELLITE_REGEX = Regex("""(\S+卫视)""")
        private val SEPARATOR_REGEX = Regex("""[\s\-_.:/，,]+""")
        private val QUALITY_KEYWORDS = listOf(
            "1080p", "720p", "4k", "8k", "fhd", "uhd", "hd", "sd",
            "hevc", "h265", "h264", "ipv6", "ipv4",
            "超高清", "高清", "超清", "标清", "蓝光", "测试", "备用", "专线"
        )
        private val CCTV_REGIONS = listOf("欧洲", "美洲", "北美", "亚洲")
        private val SATELLITE_GENERIC_SUFFIXES = setOf("", "综合", "频道", "综合频道", "台")
    }
}
