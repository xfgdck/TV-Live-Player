package top.xiaofeigun.tvliveplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import top.xiaofeigun.tvliveplayer.data.local.dao.ChannelDao
import top.xiaofeigun.tvliveplayer.data.local.dao.SourceDao
import top.xiaofeigun.tvliveplayer.data.local.entity.ChannelEntity
import top.xiaofeigun.tvliveplayer.data.local.entity.SourceEntity

@Database(
    entities = [ChannelEntity::class, SourceEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun sourceDao(): SourceDao
}
