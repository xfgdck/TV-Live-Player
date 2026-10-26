package top.xiaofeigun.tvliveplayer.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** 在线 M3U/TXT 订阅下载器（宽松证书策略，兼容自签名直播源） */
@Singleton
class PlaylistFetcher @Inject constructor(
    @param:Named("unsafeOkHttpClient") private val okHttpClient: OkHttpClient
) {
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("服务器返回 HTTP ${response.code}")
            response.body?.string()?.takeIf { it.isNotBlank() } ?: throw IOException("返回内容为空")
        }
    }
}
