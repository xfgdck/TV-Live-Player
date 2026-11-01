package top.xiaofeigun.tvliveplayer.domain.model

import java.net.URLDecoder

/**
 * 带请求头的播放地址。沿用 Kodi 约定以单个字符串存储：`url|User-Agent=xxx&Referer=yyy`，
 * 头部值中的 `%`、`&`、`|` 以百分号编码，便于与频道的其它播放地址一起持久化和去重。
 */
data class StreamUrl(val url: String, val headers: Map<String, String> = emptyMap()) {

    fun encode(): String {
        if (headers.isEmpty()) return url
        return url + "|" + headers.entries.joinToString("&") { (k, v) -> "$k=${encodeValue(v)}" }
    }

    companion object {
        const val USER_AGENT = "User-Agent"
        const val REFERER = "Referer"

        private val PLAYABLE_SCHEMES = listOf("http://", "https://", "rtsp://", "udp://")

        /** TVPlayer 可播放的协议：http(s) 走 OkHttp，rtsp 走 RtspMediaSource，udp 组播走 UdpDataSource */
        fun isPlayable(url: String): Boolean = PLAYABLE_SCHEMES.any { url.startsWith(it, ignoreCase = true) }

        /** OkHttp 只接受 ASCII 可见字符的头部名与值；不合法的头部会让该地址必然失败，直接丢弃 */
        fun isValidHeader(name: String, value: String): Boolean =
            name.isNotEmpty() && name.all { it in '\u0021'..'\u007e' && it != ':' } &&
                value.all { it == '\t' || it in '\u0020'..'\u007e' }

        fun parse(raw: String): StreamUrl {
            val pipe = raw.indexOf('|')
            if (pipe < 0) return StreamUrl(raw.trim())
            val headers = linkedMapOf<String, String>()
            for (pair in raw.substring(pipe + 1).split('&')) {
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                val name = headerName(pair.substring(0, eq))
                val value = decodeValue(pair.substring(eq + 1).trim())
                if (value.isNotEmpty() && isValidHeader(name, value)) headers[name] = value
            }
            return StreamUrl(raw.substring(0, pipe).trim(), headers)
        }

        /** 统一常见头部名称的写法（http-user-agent、referrer 等 M3U 惯用别名） */
        fun headerName(raw: String): String = when (val key = raw.trim().lowercase()) {
            "user-agent", "http-user-agent", "useragent" -> USER_AGENT
            "referer", "referrer", "http-referer", "http-referrer" -> REFERER
            else -> raw.trim().takeIf { key.isNotEmpty() }.orEmpty()
        }

        private fun encodeValue(value: String): String =
            value.replace("%", "%25").replace("&", "%26").replace("|", "%7C")

        /** 只解码 %XX，保留 `+` 原样（UA 中常见，且不少源直接写未编码的原文） */
        private fun decodeValue(value: String): String {
            if ('%' !in value) return value
            return try {
                URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) {
                value
            }
        }
    }
}
