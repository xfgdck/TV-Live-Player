package com.wangyg.tvliveplayer.player

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
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

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun initialize(surfaceView: SurfaceView) {
        release()
        exoPlayer = ExoPlayer.Builder(context)
            .build()
            .also { player ->
                player.setVideoSurfaceView(surfaceView)
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _playbackState.value = when (state) {
                            Player.STATE_IDLE -> PlaybackState.IDLE
                            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                            Player.STATE_READY -> PlaybackState.READY
                            Player.STATE_ENDED -> PlaybackState.IDLE
                            else -> PlaybackState.IDLE
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _playbackState.value = PlaybackState.ERROR
                        _errorMessage.value = error.localizedMessage ?: "播放错误"
                    }
                })
            }
    }

    fun play(url: String) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))
        exoPlayer?.apply {
            setMediaSource(mediaSource)
            prepare()
            play()
        }
        _errorMessage.value = null
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
        _playbackState.value = PlaybackState.IDLE
        _errorMessage.value = null
    }
}
