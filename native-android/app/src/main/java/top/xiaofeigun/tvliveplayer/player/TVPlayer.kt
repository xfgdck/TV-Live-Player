package top.xiaofeigun.tvliveplayer.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackState {
    IDLE,
    BUFFERING,
    READY,
    ERROR
}

@Singleton
class TVPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private var _exoPlayer: ExoPlayer? = null
    val player: ExoPlayer? get() = _exoPlayer

    /** 拉流使用系统标准 SSL 校验（见 AGENTS.md 6.4） */
    private val dataSourceFactory: DataSource.Factory by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()
        OkHttpDataSource.Factory(okHttpClient)
    }

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var urlList: List<String> = emptyList()
    private var urlIndex: Int = 0
    /** 全部源失败后的整轮自动重试次数 */
    private var retryRound: Int = 0
    private val retryHandler = Handler(Looper.getMainLooper())

    fun initialize() {
        if (_exoPlayer != null) return
        _exoPlayer = ExoPlayer.Builder(context).build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _playbackState.value = when (state) {
                            Player.STATE_IDLE -> PlaybackState.IDLE
                            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                            Player.STATE_READY -> {
                                retryRound = 0
                                PlaybackState.READY
                            }
                            Player.STATE_ENDED -> {
                                // 直播流意外结束：重启拉流
                                player.seekTo(0)
                                player.play()
                                PlaybackState.BUFFERING
                            }
                            else -> PlaybackState.IDLE
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        handleError(player, error)
                    }
                })
            }
    }

    private fun handleError(player: ExoPlayer, error: PlaybackException) {
        // HLS 直播窗口落后（长时间暂停/卡顿后常见）：回到直播点即可，无需切源
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        val nextIdx = urlIndex + 1
        if (nextIdx < urlList.size) {
            urlIndex = nextIdx
            _playbackState.value = PlaybackState.BUFFERING
            _errorMessage.value = "$SWITCHING_PREFIX(${nextIdx + 1}/${urlList.size})..."
            retryHandler.postDelayed({ playCurrent() }, SWITCH_DELAY_MS)
            return
        }
        _playbackState.value = PlaybackState.ERROR
        if (retryRound < MAX_RETRY_ROUNDS) {
            retryRound++
            _errorMessage.value = "所有源均无法播放，${RETRY_DELAY_MS / 1000} 秒后自动重试 ($retryRound/$MAX_RETRY_ROUNDS)"
            retryHandler.postDelayed({
                urlIndex = 0
                _playbackState.value = PlaybackState.BUFFERING
                playCurrent()
            }, RETRY_DELAY_MS)
        } else {
            _errorMessage.value = error.localizedMessage ?: "所有源均无法播放"
        }
    }

    fun playUrls(urls: List<String>, startIndex: Int = 0) {
        urlList = urls
        urlIndex = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        retryRound = 0
        retryHandler.removeCallbacksAndMessages(null)
        if (urls.isEmpty()) {
            _playbackState.value = PlaybackState.IDLE
            _errorMessage.value = null
            return
        }
        _playbackState.value = PlaybackState.BUFFERING
        playCurrent()
    }

    private fun playCurrent() {
        val url = urlList.getOrNull(urlIndex) ?: return
        _exoPlayer?.apply {
            setMediaSource(createMediaSource(url))
            prepare()
            play()
        }
        _errorMessage.value = null
    }

    private fun createMediaSource(url: String): MediaSource {
        val item = MediaItem.fromUri(url)
        return if (isHls(url)) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        }
    }

    /** 扩展名识别 + 常见无扩展名 HLS 直播平台兜底 */
    private fun isHls(url: String): Boolean {
        if (Util.inferContentType(Uri.parse(url)) == C.CONTENT_TYPE_HLS) return true
        val lower = url.lowercase()
        return HLS_HINTS.any { lower.contains(it) }
    }

    fun pause() {
        _exoPlayer?.pause()
    }

    fun resume() {
        _exoPlayer?.play()
    }

    fun isPlaying(): Boolean = _exoPlayer?.isPlaying ?: false

    fun release() {
        retryHandler.removeCallbacksAndMessages(null)
        _exoPlayer?.release()
        _exoPlayer = null
        urlList = emptyList()
        urlIndex = 0
        retryRound = 0
        _playbackState.value = PlaybackState.IDLE
        _errorMessage.value = null
    }

    companion object {
        /** 切换备用源提示前缀，UI 据此区分「切源中」与「播放失败」 */
        const val SWITCHING_PREFIX = "正在切换源"
        private const val SWITCH_DELAY_MS = 3000L
        private const val RETRY_DELAY_MS = 10_000L
        private const val MAX_RETRY_ROUNDS = 3
        private val HLS_HINTS = listOf("m3u8", "huya", "douyu")
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
