package top.xiaofeigun.tvliveplayer.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import top.xiaofeigun.tvliveplayer.data.local.AppDatabase
import top.xiaofeigun.tvliveplayer.data.local.dao.ChannelDao
import top.xiaofeigun.tvliveplayer.data.local.dao.SourceDao
import top.xiaofeigun.tvliveplayer.data.repository.ChannelRepositoryImpl
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private const val DATABASE_NAME = "tv_live_player.db"

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChannelDao(database: AppDatabase): ChannelDao = database.channelDao()

    @Provides
    fun provideSourceDao(database: AppDatabase): SourceDao = database.sourceDao()

    @Provides
    @Singleton
    fun provideChannelRepository(repository: ChannelRepositoryImpl): ChannelRepository = repository
}
