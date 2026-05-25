package com.wangyg.tvliveplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wangyg.tvliveplayer.data.local.dao.ChannelDao
import com.wangyg.tvliveplayer.data.local.dao.SourceDao
import com.wangyg.tvliveplayer.data.local.entity.ChannelEntity
import com.wangyg.tvliveplayer.data.local.entity.SourceEntity

@Database(
    entities = [ChannelEntity::class, SourceEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun sourceDao(): SourceDao
}
