package com.wangyg.tvliveplayer.ui.player

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.FragmentActivity
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.player.PlaybackState
import com.wangyg.tvliveplayer.player.TVPlayer
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
    private lateinit var btnRetry: View
    private lateinit var controlsLayout: View
    private lateinit var sideControls: View
    private lateinit var btnPlayPause: ImageButton

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
        btnRetry = findViewById(R.id.btn_retry)
        controlsLayout = findViewById(R.id.controls_layout)
        sideControls = findViewById(R.id.side_controls)
        btnPlayPause = findViewById(R.id.btn_play_pause)

        tvPlayer.initialize(surfaceView)

        setupTouch()
        setupButtons()

        val channelId = intent.getStringExtra("channel_id")
        val channelName = intent.getStringExtra("channel_name")
        if (channelName != null) {
            viewModel.playChannelByName(channelName)
        } else if (channelId != null) {
            viewModel.playChannelById(channelId)
        }

        observeState()
    }

    private fun setupTouch() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                finish()
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

        val content = findViewById<View>(android.R.id.content)
        content.setOnTouchListener { _, event ->
            val side = sideControls
            if (side.visibility == View.VISIBLE) {
                val x = event.x
                val width = content.width
                if (x < width - side.width) {
                    hideControls()
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btn_prev_channel).setOnClickListener {
            viewModel.previousChannel()
            showControls()
        }
        findViewById<View>(R.id.btn_next_channel).setOnClickListener {
            viewModel.nextChannel()
            showControls()
        }
        findViewById<View>(R.id.btn_play_pause).setOnClickListener {
            if (tvPlayer.isPlaying()) {
                tvPlayer.pause()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                tvPlayer.resume()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
            showControls()
        }
        findViewById<View>(R.id.btn_exit).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.btn_channel_list).setOnClickListener {
            showChannelList()
        }
        findViewById<View>(R.id.btn_side_up).setOnClickListener {
            viewModel.previousChannel()
        }
        findViewById<View>(R.id.btn_side_down).setOnClickListener {
            viewModel.nextChannel()
        }
        findViewById<View>(R.id.btn_retry).setOnClickListener {
            viewModel.currentChannel.value?.let {
                tvPlayer.play(it.url)
            }
        }
    }

    private fun toggleControls() {
        if (controlsLayout.visibility == View.VISIBLE) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun showControls() {
        osdLayout.visibility = View.VISIBLE
        controlsLayout.visibility = View.VISIBLE
        sideControls.visibility = View.VISIBLE
        osdHandler.removeCallbacksAndMessages(null)
        osdHandler.postDelayed({ hideControls() }, 5000L)
    }

    private fun hideControls() {
        osdLayout.visibility = View.GONE
        controlsLayout.visibility = View.GONE
        sideControls.visibility = View.GONE
    }

    private fun showChannelList() {
        val channels = viewModel.channels.value
        if (channels.isEmpty()) return
        val names = channels.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.channel_list)
            .setItems(names) { _, which ->
                viewModel.playChannel(channels[which])
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.currentChannel.collectLatest { channel ->
                if (channel != null) {
                    tvPlayer.play(channel.url)
                    tvChannelName.text = channel.name
                    tvCategory.text = channel.category
                    showControls()
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
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
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
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showControls(); true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish(); true
            }
            KeyEvent.KEYCODE_MENU -> {
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
