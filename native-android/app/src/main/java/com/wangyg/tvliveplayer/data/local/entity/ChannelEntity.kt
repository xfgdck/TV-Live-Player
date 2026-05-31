package com.wangyg.tvliveplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wangyg.tvliveplayer.domain.model.Channel

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val backupUrls: String = "",
    val category: String,
    val logo: String? = null,
    val tvgId: String? = null,
    val sortOrder: Int = 0,
    val favorite: Int = 0
) {
    fun toDomainModel(): Channel = Channel(
        id = id,
        name = name,
        url = url,
        backupUrls = if (backupUrls.isBlank()) emptyList() else backupUrls.split("|||"),
        category = category,
        logo = logo,
        tvgId = tvgId,
        sortOrder = sortOrder,
        isFavorite = favorite == 1
    )

    companion object {
        fun fromDomainModel(channel: Channel): ChannelEntity = ChannelEntity(
            id = channel.id,
            name = channel.name,
            url = channel.url,
            backupUrls = channel.backupUrls.joinToString("|||"),
            category = channel.category,
            logo = channel.logo,
            tvgId = channel.tvgId,
            sortOrder = channel.sortOrder,
            favorite = if (channel.isFavorite) 1 else 0
        )
    }
}
