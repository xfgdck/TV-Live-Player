package com.wangyg.tvliveplayer.data.repository

import com.wangyg.tvliveplayer.domain.model.Channel
import java.util.UUID

object DefaultChannels {

    fun getDefaultChannels(): List<Channel> = listOf(
        // CGTN国际
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN",
            url = "https://live.cgtn.com/1000/prog_index.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn",
            sortOrder = 0
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Documentary",
            url = "https://live.cgtn.com/1000d/prog_index.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.documentary",
            sortOrder = 1
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Español",
            url = "https://live.cgtn.com/2000/prog_index.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.espanol",
            sortOrder = 2
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Français",
            url = "https://live.cgtn.com/3000/prog_index.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.francais",
            sortOrder = 3
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN العربية",
            url = "https://live.cgtn.com/4000/prog_index.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.arabic",
            sortOrder = 4
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Russian",
            url = "https://live.cgtn.com/5000/prog_index.m3u8",
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.russian",
            sortOrder = 5
        ),
        // 纪录纪实
        Channel(
            id = UUID.randomUUID().toString(),
            name = "NASA TV",
            url = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-1/master.m3u8",
            category = "纪录纪实",
            logo = null,
            tvgId = "nasa.tv",
            sortOrder = 6
        ),
        // 海外新闻
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Newsmax",
            url = "https://nmxlive.akamaized.net/hls/live/2102843/NMX_News_2/master.m3u8",
            category = "海外新闻",
            logo = null,
            tvgId = "newsmax",
            sortOrder = 7
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Al Jazeera English",
            url = "https://live-hls-web-aje.getaj.net/AJE/index.m3u8",
            category = "海外新闻",
            logo = null,
            tvgId = "aljazeera.english",
            sortOrder = 8
        ),
        // 体育运动
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Red Bull TV",
            url = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            category = "体育运动",
            logo = null,
            tvgId = "redbull.tv",
            sortOrder = 9
        ),
        // 演示测试
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Test 1",
            url = "https://live.cgtn.com/1000/prog_index.m3u8",
            category = "演示测试",
            logo = null,
            tvgId = null,
            sortOrder = 10
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Test 2",
            url = "https://live.cgtn.com/1000d/prog_index.m3u8",
            category = "演示测试",
            logo = null,
            tvgId = null,
            sortOrder = 11
        )
    )
}
