package com.wangyg.tvliveplayer.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
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
import com.wangyg.tvliveplayer.ui.settings.SettingsDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
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

    private val osdHandler = Handler(Looper.getMainLooper())
    private val OSD_TIMEOUT_MS = 3000L
    private lateinit var gestureDetector: GestureDetector

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

        tvPlayer.initialize(surfaceView)

        setupTouch()

        val channelName = intent.getStringExtra("channel_name")
        if (channelName != null) {
            viewModel.playChannelByName(channelName)
        }

        observeState()
    }

    private fun setupTouch() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleOsd()
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                if (abs(diffY) > 100) {
                    if (diffY < 0) viewModel.nextChannel()
                    else viewModel.previousChannel()
                    return true
                }
                return false
            }
        })

        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun toggleOsd() {
        if (osdLayout.visibility == View.VISIBLE) {
            osdLayout.visibility = View.GONE
            osdHandler.removeCallbacksAndMessages(null)
        } else {
            osdLayout.visibility = View.VISIBLE
            osdHandler.removeCallbacksAndMessages(null)
            osdHandler.postDelayed({ osdLayout.visibility = View.GONE }, OSD_TIMEOUT_MS)
        }
    }

    private fun showOsd() {
        osdLayout.visibility = View.VISIBLE
        osdHandler.removeCallbacksAndMessages(null)
        osdHandler.postDelayed({ osdLayout.visibility = View.GONE }, OSD_TIMEOUT_MS)
    }

    private fun observeState() {
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

        lifecycleScope.launch {
            viewModel.showOsd.collectLatest { show ->
                osdLayout.visibility = if (show) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { gestureDetector.onTouchEvent(it) }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.previousChannel(); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.nextChannel(); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                viewModel.previousCategory(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                viewModel.nextCategory(); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showOsd(); true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish(); true
            }
            KeyEvent.KEYCODE_MENU -> {
                SettingsDialogFragment().show(supportFragmentManager, "SettingsDialog"); true
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
