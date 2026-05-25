package com.wangyg.tvliveplayer.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.FragmentActivity
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.player.PlaybackState
import com.wangyg.tvliveplayer.player.TVPlayer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : FragmentActivity() {

    @Inject lateinit var tvPlayer: TVPlayer
    private val viewModel: PlayerViewModel by viewModels()

    private lateinit var surfaceView: SurfaceView
    private lateinit var osdLayout: View
    private lateinit var tvChannelName: TextView
    private lateinit var tvCategory: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvWelcome: TextView
    private lateinit var btnRetry: View

    private val osdHandler = Handler(Looper.getMainLooper())
    private val OSD_TIMEOUT_MS = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        surfaceView = findViewById(R.id.surface_view)
        osdLayout = findViewById(R.id.osd_layout)
        tvChannelName = findViewById(R.id.tv_channel_name)
        tvCategory = findViewById(R.id.tv_category)
        progressBar = findViewById(R.id.progress_bar)
        tvError = findViewById(R.id.tv_error)
        tvWelcome = findViewById(R.id.tv_welcome)
        btnRetry = findViewById(R.id.btn_retry)

        tvPlayer.initialize(surfaceView)

        // 接收从Intent传入的频道ID
        val channelId = intent.getStringExtra("channel_id")
        if (channelId != null) {
            viewModel.playChannelById(channelId)
        }

        observeState()
    }

    private fun observeState() {
        // 观察当前频道
        lifecycleScope.launch {
            viewModel.currentChannel.collectLatest { channel ->
                if (channel != null) {
                    tvPlayer.play(channel.url)
                    tvChannelName.text = channel.name
                    tvCategory.text = channel.category
                    showOsd()
                }
            }
        }

        // 观察播放状态
        lifecycleScope.launch {
            tvPlayer.playbackState.collectLatest { state ->
                when (state) {
                    PlaybackState.BUFFERING -> {
                        progressBar.visibility = View.VISIBLE
                        tvError.visibility = View.GONE
                        tvWelcome.visibility = View.GONE
                    }
                    PlaybackState.READY -> {
                        progressBar.visibility = View.GONE
                        tvError.visibility = View.GONE
                        tvWelcome.visibility = View.GONE
                    }
                    PlaybackState.ERROR -> {
                        progressBar.visibility = View.GONE
                        tvError.visibility = View.VISIBLE
                        tvWelcome.visibility = View.GONE
                    }
                    PlaybackState.IDLE -> {
                        if (viewModel.channels.value.isEmpty()) {
                            tvWelcome.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        // 观察OSD
        lifecycleScope.launch {
            viewModel.showOsd.collectLatest { show ->
                osdLayout.visibility = if (show) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showOsd() {
        viewModel.toggleOsd()
        if (viewModel.showOsd.value) {
            osdHandler.removeCallbacksAndMessages(null)
            osdHandler.postDelayed({ viewModel.hideOsd() }, OSD_TIMEOUT_MS)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.previousChannel(); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.nextChannel(); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showOsd(); true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish(); true
            }
            KeyEvent.KEYCODE_MENU -> {
                // 返回浏览界面
                finish(); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tvPlayer.release()
        osdHandler.removeCallbacksAndMessages(null)
    }
}
