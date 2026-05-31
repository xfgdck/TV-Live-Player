package com.wangyg.tvliveplayer.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackState {
    IDLE,
    BUFFERING,
    READY,
    ERROR
}

@Singleton
class TVPlayer @Inject constructor(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var dataSourceFactory: DataSource.Factory? = null

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var urlList: List<String> = emptyList()
    private var urlIndex: Int = 0
    private var retryHandler: Handler? = null

    fun initialize(textureView: TextureView) {
        if (exoPlayer != null) {
            exoPlayer?.setVideoTextureView(textureView)
            return
        }
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()
        dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        retryHandler = Handler(Looper.getMainLooper())
        exoPlayer = ExoPlayer.Builder(context).build()
            .also { player ->
                player.setVideoTextureView(textureView)
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _playbackState.value = when (state) {
                            Player.STATE_IDLE -> PlaybackState.IDLE
                            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                            Player.STATE_READY -> PlaybackState.READY
                            Player.STATE_ENDED -> {
                                player.seekTo(0)
                                player.play()
                                PlaybackState.BUFFERING
                            }
                            else -> PlaybackState.IDLE
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val nextIdx = urlIndex + 1
                        if (nextIdx < urlList.size) {
                            urlIndex = nextIdx
                            val nextUrl = urlList[nextIdx]
                            _errorMessage.value = "正在切换源(${nextIdx + 1}/${urlList.size})..."
                            retryHandler?.postDelayed({
                                playCurrent()
                            }, 3000)
                        } else {
                            _playbackState.value = PlaybackState.ERROR
                            _errorMessage.value = error.localizedMessage ?: "所有源均无法播放"
                        }
                    }
                })
            }
    }

    fun playUrls(urls: List<String>, startIndex: Int = 0) {
        urlList = urls
        urlIndex = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        retryHandler?.removeCallbacksAndMessages(null)
        if (urls.isEmpty()) {
            _playbackState.value = PlaybackState.IDLE
            return
        }
        playCurrent()
    }

    private fun playCurrent() {
        if (urlIndex >= urlList.size) {
            _playbackState.value = PlaybackState.ERROR
            _errorMessage.value = "所有源均无法播放"
            return
        }
        val url = urlList[urlIndex]
        val dsf = dataSourceFactory
            ?: OkHttpDataSource.Factory(
                OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
            )
        val mediaSource = createMediaSource(url, dsf)
        exoPlayer?.apply {
            setMediaSource(mediaSource)
            prepare()
            play()
        }
        _errorMessage.value = null
    }

    private fun createMediaSource(url: String, dsf: DataSource.Factory): MediaSource {
        return if (url.contains(".m3u8") || url.contains("m3u8") || url.contains("huya") || url.contains("douyu")) {
            HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url))
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false

    fun release() {
        retryHandler?.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
        dataSourceFactory = null
        urlList = emptyList()
        urlIndex = 0
        _playbackState.value = PlaybackState.IDLE
        _errorMessage.value = null
    }
}
