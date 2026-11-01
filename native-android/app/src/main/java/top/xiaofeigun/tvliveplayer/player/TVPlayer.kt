package top.xiaofeigun.tvliveplayer.player

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import top.xiaofeigun.tvliveplayer.domain.model.StreamUrl
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

    /**
     * 拉流使用系统标准 SSL 校验（见 AGENTS.md 6.4）。
     * 未指定 User-Agent 的地址使用默认 UA；地址自带的请求头（见 [StreamUrl]）优先。
     */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header(StreamUrl.USER_AGENT) != null) {
                    chain.proceed(request)
                } else {
                    chain.proceed(request.newBuilder().header(StreamUrl.USER_AGENT, USER_AGENT).build())
                }
            }
            .build()
    }

    /** DefaultDataSource 按协议分发：http(s) 交给 OkHttp（携带该地址的请求头），udp:// 组播交给内置 UdpDataSource */
    private fun dataSourceFactory(headers: Map<String, String>): DataSource.Factory =
        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient).setDefaultRequestProperties(headers))

    /** Wi-Fi 下接收组播需持有 MulticastLock，否则多数设备会丢弃组播包 */
    private val multicastLock: WifiManager.MulticastLock? by lazy {
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("TVPlayer")
            ?.apply { setReferenceCounted(false) }
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
        // 部分盒子硬解初始化失败时自动回退到其它解码器
        val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        _exoPlayer = ExoPlayer.Builder(context, renderersFactory).build()
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

    fun playUrls(urls: List<String>) {
        // 兼容旧版本入库的 rtmp / p2p 等不支持协议，避免逐个失败白等
        urlList = urls.filter(StreamUrl::isPlayable)
        urlIndex = 0
        retryRound = 0
        retryHandler.removeCallbacksAndMessages(null)
        if (urlList.isEmpty()) {
            if (urls.isEmpty()) {
                _playbackState.value = PlaybackState.IDLE
                _errorMessage.value = null
            } else {
                _playbackState.value = PlaybackState.ERROR
                _errorMessage.value = "该频道的播放地址协议暂不支持"
            }
            return
        }
        _playbackState.value = PlaybackState.BUFFERING
        playCurrent()
    }

    /** 同一频道的地址列表变化（如刷新订阅）：当前地址仍在则无缝沿用；已被移除或已全部失败时按新列表重新播放 */
    fun updateUrls(urls: List<String>) {
        val playable = urls.filter(StreamUrl::isPlayable)
        val index = urlList.getOrNull(urlIndex)?.let { playable.indexOf(it) } ?: -1
        if (index < 0 || _playbackState.value == PlaybackState.ERROR) {
            playUrls(urls)
        } else {
            urlList = playable
            urlIndex = index
        }
    }

    private fun playCurrent() {
        val url = urlList.getOrNull(urlIndex) ?: return
        if (url.startsWith("udp://", ignoreCase = true)) multicastLock?.acquire() else multicastLock?.release()
        _exoPlayer?.apply {
            setMediaSource(createMediaSource(url))
            prepare()
            play()
        }
        _errorMessage.value = null
    }

    private fun createMediaSource(raw: String): MediaSource {
        val stream = StreamUrl.parse(raw)
        val url = stream.url
        val item = MediaItem.fromUri(url)
        return when {
            // RTSP 自带 RTP 传输层，不经过 DataSource；UDP 不通时自动回退 TCP
            url.startsWith("rtsp://", ignoreCase = true) ->
                RtspMediaSource.Factory()
                    .setUserAgent(stream.headers[StreamUrl.USER_AGENT] ?: USER_AGENT)
                    .createMediaSource(item)
            isHls(url) -> HlsMediaSource.Factory(dataSourceFactory(stream.headers)).createMediaSource(item)
            Util.inferContentType(Uri.parse(url)) == C.CONTENT_TYPE_DASH ->
                DashMediaSource.Factory(dataSourceFactory(stream.headers)).createMediaSource(item)
            // http(s) 下的 FLV / TS / MP4 等，以及 udp:// 组播 TS
            else -> ProgressiveMediaSource.Factory(dataSourceFactory(stream.headers)).createMediaSource(item)
        }
    }

    /** 扩展名识别 + 常见无扩展名 HLS 直播平台兜底 */
    private fun isHls(url: String): Boolean {
        if (Util.inferContentType(Uri.parse(url)) == C.CONTENT_TYPE_HLS) return true
        val lower = url.lowercase()
        return HLS_HINTS.any { lower.contains(it) }
    }

    /** 应用退到后台：停止拉流并释放网络与解码资源，保留当前频道与正在使用的源 */
    fun stop() {
        retryHandler.removeCallbacksAndMessages(null)
        multicastLock?.release()
        _exoPlayer?.stop()
        _errorMessage.value = null
    }

    /** 回到前台：从当前源重新拉流，直接回到直播点，避免播放后台期间的陈旧缓冲 */
    fun restart() {
        if (urlList.isEmpty()) return
        retryRound = 0
        _playbackState.value = PlaybackState.BUFFERING
        playCurrent()
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
        multicastLock?.release()
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
