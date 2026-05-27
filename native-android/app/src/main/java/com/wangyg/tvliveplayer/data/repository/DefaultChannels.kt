package com.wangyg.tvliveplayer.data.repository

import com.wangyg.tvliveplayer.domain.model.Channel
import java.util.UUID

object DefaultChannels {

    fun getDefaultChannels(): List<Channel> = listOf(
        // CGTN国际
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN",
            url = "https://news.cgtn.com/resource/live/english/cgtn-news.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn",
            sortOrder = 0
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Documentary",
            url = "https://news.cgtn.com/resource/live/document/cgtn-doc.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.documentary",
            sortOrder = 1
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Español",
            url = "https://news.cgtn.com/resource/live/espanol/cgtn-e.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.espanol",
            sortOrder = 2
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Français",
            url = "https://news.cgtn.com/resource/live/french/cgtn-f.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.francais",
            sortOrder = 3
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN العربية",
            url = "https://news.cgtn.com/resource/live/arabic/cgtn-a.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.arabic",
            sortOrder = 4
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Russian",
            url = "https://news.cgtn.com/resource/live/russian/cgtn-r.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.russian",
            sortOrder = 5
        ),
        // 新闻
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CCTV-13 新闻",
            url = "http://112.123.243.37:50085/tsfile/live/0014_1.m3u8?key=txiptv&playlive=0&authid=0",
            category = "新闻",
            logo = null,
            tvgId = "cctv13",
            sortOrder = 6
        ),
        // 体育
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CCTV-5 体育",
            url = "http://112.123.243.37:50085/tsfile/live/0005_1.m3u8?key=txiptv&playlive=0&authid=0",
            category = "体育",
            logo = null,
            tvgId = "cctv5",
            sortOrder = 7
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CCTV-5+",
            url = "http://112.123.243.37:50085/tsfile/live/0006_1.m3u8?key=txiptv&playlive=0&authid=0",
            category = "体育",
            logo = null,
            tvgId = "cctv5plus",
            sortOrder = 8
        )
    )
}
