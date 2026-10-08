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
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideChannelDao(database: AppDatabase): ChannelDao = database.channelDao()

    @Provides
    fun provideSourceDao(database: AppDatabase): SourceDao = database.sourceDao()

    @Provides
    @Singleton
    fun provideChannelRepository(repository: ChannelRepositoryImpl): ChannelRepository = repository

    @Provides
    @Singleton
    @javax.inject.Named("unsafeOkHttpClient")
    fun provideUnsafeOkHttpClient(): okhttp3.OkHttpClient {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return okhttp3.OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
