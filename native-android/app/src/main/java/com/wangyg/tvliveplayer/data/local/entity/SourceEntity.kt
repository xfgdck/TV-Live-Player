package com.wangyg.tvliveplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wangyg.tvliveplayer.domain.model.Source

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val isActive: Int = 1,
    val createdAt: Long = 0L
) {
    fun toDomainModel(): Source = Source(
        id = id,
        name = name,
        url = url,
        isActive = isActive == 1,
        createdAt = createdAt
    )

    companion object {
        fun fromDomainModel(source: Source): SourceEntity = SourceEntity(
            id = source.id,
            name = source.name,
            url = source.url,
            isActive = if (source.isActive) 1 else 0,
            createdAt = source.createdAt
        )
    }
}
