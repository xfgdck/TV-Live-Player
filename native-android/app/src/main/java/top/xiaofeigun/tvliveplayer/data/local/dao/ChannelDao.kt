package top.xiaofeigun.tvliveplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import top.xiaofeigun.tvliveplayer.data.local.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels ORDER BY sortOrder ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE category = :category ORDER BY sortOrder ASC")
    fun getChannelsByCategory(category: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE favorite = 1 ORDER BY sortOrder ASC")
    fun getFavoriteChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getChannelById(id: String): ChannelEntity?

    @Query("SELECT * FROM channels ORDER BY sortOrder ASC")
    suspend fun getAllChannelsList(): List<ChannelEntity>

    @Query("SELECT MAX(sortOrder) FROM channels")
    suspend fun getMaxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Update
    suspend fun updateAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE channels SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: String, category: String)

    @Query("UPDATE channels SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Int)

    @Delete
    suspend fun deleteChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()

    @Query("DELETE FROM channels WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("UPDATE channels SET category = :to WHERE category = :from")
    suspend fun moveCategory(from: String, to: String)
}
