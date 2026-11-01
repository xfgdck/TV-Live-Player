package top.xiaofeigun.tvliveplayer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import top.xiaofeigun.tvliveplayer.domain.model.Source

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val isActive: Int = 1,
    val createdAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val lastUpdated: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val channelCount: Int = 0,
    val lastError: String? = null
) {
    fun toDomainModel(): Source = Source(
        id = id,
        name = name,
        url = url,
        isActive = isActive == 1,
        createdAt = createdAt,
        lastUpdated = lastUpdated,
        channelCount = channelCount,
        lastError = lastError
    )

    companion object {
        fun fromDomainModel(source: Source): SourceEntity = SourceEntity(
            id = source.id,
            name = source.name,
            url = source.url,
            isActive = if (source.isActive) 1 else 0,
            createdAt = source.createdAt,
            lastUpdated = source.lastUpdated,
            channelCount = source.channelCount,
            lastError = source.lastError
        )
    }
}
