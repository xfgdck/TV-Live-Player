package com.wangyg.tvliveplayer.domain.model

data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val logo: String? = null,
    val tvgId: String? = null,
    val sortOrder: Int = 0,
    val isFavorite: Boolean = false
)
