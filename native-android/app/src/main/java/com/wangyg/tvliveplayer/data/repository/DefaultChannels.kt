package com.wangyg.tvliveplayer.data.repository

import com.wangyg.tvliveplayer.domain.model.Channel
import java.util.UUID

object DefaultChannels {

    fun getDefaultChannels(): List<Channel> = listOf(
        // CGTN国际
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN",
            url = "", // TODO: fill with actual stream URL
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn",
            sortOrder = 0
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Documentary",
            url = "", // TODO: fill with actual stream URL
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.documentary",
            sortOrder = 1
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Español",
            url = "", // TODO: fill with actual stream URL
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.espanol",
            sortOrder = 2
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Français",
            url = "", // TODO: fill with actual stream URL
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.francais",
            sortOrder = 3
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN العربية",
            url = "", // TODO: fill with actual stream URL
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.arabic",
            sortOrder = 4
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "CGTN Russian",
            url = "", // TODO: fill with actual stream URL
            category = "CGTN国际",
            logo = null,
            tvgId = "cgtn.russian",
            sortOrder = 5
        ),
        // 纪录纪实
        Channel(
            id = UUID.randomUUID().toString(),
            name = "NASA TV",
            url = "", // TODO: fill with actual stream URL
            category = "纪录纪实",
            logo = null,
            tvgId = "nasa.tv",
            sortOrder = 6
        ),
        // 海外新闻
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Newsmax",
            url = "", // TODO: fill with actual stream URL
            category = "海外新闻",
            logo = null,
            tvgId = "newsmax",
            sortOrder = 7
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Al Jazeera English",
            url = "", // TODO: fill with actual stream URL
            category = "海外新闻",
            logo = null,
            tvgId = "aljazeera.english",
            sortOrder = 8
        ),
        // 体育运动
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Red Bull TV",
            url = "", // TODO: fill with actual stream URL
            category = "体育运动",
            logo = null,
            tvgId = "redbull.tv",
            sortOrder = 9
        ),
        // 演示测试
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Test 1",
            url = "", // TODO: fill with test stream URL
            category = "演示测试",
            logo = null,
            tvgId = null,
            sortOrder = 10
        ),
        Channel(
            id = UUID.randomUUID().toString(),
            name = "Test 2",
            url = "", // TODO: fill with test stream URL
            category = "演示测试",
            logo = null,
            tvgId = null,
            sortOrder = 11
        )
    )
}
