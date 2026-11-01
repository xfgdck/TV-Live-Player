package top.xiaofeigun.tvliveplayer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import top.xiaofeigun.tvliveplayer.domain.model.Channel

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
    val favorite: Int = 0,
    /** 地址所属订阅，每行 `url<TAB>订阅 id`（见 [Channel.urlSources]） */
    @ColumnInfo(defaultValue = "''")
    val urlSources: String = ""
) {
    fun allUrls(): List<String> =
        listOf(url) + if (backupUrls.isBlank()) emptyList() else backupUrls.split("|||")

    fun toDomainModel(): Channel = Channel(
        id = id,
        name = name,
        url = url,
        backupUrls = if (backupUrls.isBlank()) emptyList() else backupUrls.split("|||"),
        category = category,
        logo = logo,
        tvgId = tvgId,
        sortOrder = sortOrder,
        isFavorite = favorite == 1,
        urlSources = decodeUrlSources(urlSources)
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
            favorite = if (channel.isFavorite) 1 else 0,
            urlSources = encodeUrlSources(channel.urlSources)
        )

        /** 参数可空：旧版本备份 JSON 缺少该字段时 Gson 会写入 null */
        fun encodeUrlSources(sources: Map<String, String>?): String =
            sources.orEmpty().entries.joinToString("\n") { (url, sourceId) -> "$url\t$sourceId" }

        fun decodeUrlSources(raw: String): Map<String, String> =
            raw.lineSequence()
                .mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 } }
                .associate { (url, sourceId) -> url to sourceId }
    }
}
