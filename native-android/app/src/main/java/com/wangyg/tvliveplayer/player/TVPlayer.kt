package com.wangyg.tvliveplayer.player

import android.content.Context
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
                                // Stream ended — auto-restart for continuous playback
                                player.seekTo(0)
                                player.play()
                                PlaybackState.BUFFERING
                            }
                            else -> PlaybackState.IDLE
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _playbackState.value = PlaybackState.ERROR
                        _errorMessage.value = error.localizedMessage ?: "播放错误"
                        // Auto-retry once after a short delay
                        val url = currentUrl
                        if (url != null) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                play(url)
                            }, 3000)
                        }
                    }
                })
            }
    }

    private var currentUrl: String? = null

    fun play(url: String) {
        currentUrl = url
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
        exoPlayer?.release()
        exoPlayer = null
        dataSourceFactory = null
        _playbackState.value = PlaybackState.IDLE
        _errorMessage.value = null
    }
}
