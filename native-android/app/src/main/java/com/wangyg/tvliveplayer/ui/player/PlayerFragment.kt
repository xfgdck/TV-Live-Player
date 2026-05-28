package com.wangyg.tvliveplayer.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wangyg.tvliveplayer.MainActivity
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.player.PlaybackState
import com.wangyg.tvliveplayer.player.TVPlayer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class PlayerFragment : Fragment() {

    @Inject lateinit var tvPlayer: TVPlayer
    private val viewModel: PlayerViewModel by viewModels(ownerProducer = { requireActivity() })

    private lateinit var surfaceView: SurfaceView
    private lateinit var osdLayout: View
    private lateinit var tvChannelName: TextView
    private lateinit var tvCategory: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var btnRetry: Button
    private lateinit var tvWelcome: TextView
    private lateinit var tvEmptyGuide: TextView
    private lateinit var tvExitConfirm: TextView
    private lateinit var categoryOverlay: View
    private lateinit var categoryChannelList: LinearLayout
    private lateinit var categoryTabs: LinearLayout
    private lateinit var iconPanel: View
    private lateinit var btnIconSettings: ImageButton
    private lateinit var btnIconEdit: ImageButton
    private lateinit var btnIconFavorite: ImageButton

    private val osdHandler = Handler(Looper.getMainLooper())
    private val exitHandler = Handler(Looper.getMainLooper())
    private val OSD_TIMEOUT_MS = 3000L
    private var exitConfirmRunnable: Runnable? = null
    private var isIconFocus = false
    private var lastPlayedUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        surfaceView = view.findViewById(R.id.surface_view)
        osdLayout = view.findViewById(R.id.osd_layout)
        tvChannelName = view.findViewById(R.id.tv_channel_name)
        tvCategory = view.findViewById(R.id.tv_category)
        progressBar = view.findViewById(R.id.progress_bar)
        tvError = view.findViewById(R.id.tv_error)
        btnRetry = view.findViewById(R.id.btn_retry)
        tvWelcome = view.findViewById(R.id.tv_welcome)
        tvEmptyGuide = view.findViewById(R.id.tv_empty_guide)
        tvExitConfirm = view.findViewById(R.id.tv_exit_confirm)
        categoryOverlay = view.findViewById(R.id.category_overlay)
        categoryChannelList = view.findViewById(R.id.category_channel_list)
        categoryTabs = view.findViewById(R.id.category_tabs)
        iconPanel = view.findViewById(R.id.icon_panel)
        btnIconSettings = view.findViewById(R.id.btn_icon_settings)
        btnIconEdit = view.findViewById(R.id.btn_icon_edit)
        btnIconFavorite = view.findViewById(R.id.btn_icon_favorite)

        tvPlayer.initialize(surfaceView)
        setupTouch()

        btnIconSettings.setOnClickListener { onIconAction(0) }
        btnIconEdit.setOnClickListener { onIconAction(1) }
        btnIconFavorite.setOnClickListener { onIconAction(2) }

        btnIconSettings.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) iconSelectedIndex = 0
        }
        btnIconEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) iconSelectedIndex = 1
        }
        btnIconFavorite.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) iconSelectedIndex = 2
        }

        btnRetry.setOnClickListener {
            viewModel.state.value.currentChannel?.let { tvPlayer.play(it.url) }
        }

        observeState()
    }

    private var iconSelectedIndex = 0

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    updatePlayer(state)
                    updateChannelInfo(state)
                    updateIconFavorite(state)
                    updateCategoryOverlay(state)
                    updateExitConfirm(state)
                    updateEmptyState(state)
                    val url = state.currentChannel?.url
                    if (url != null && url != lastPlayedUrl) {
                        lastPlayedUrl = url
                        tvError.visibility = View.GONE
                        tvPlayer.play(url)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            tvPlayer.playbackState.collect { playbackState ->
                when (playbackState) {
                    PlaybackState.BUFFERING -> {
                        progressBar.visibility = View.VISIBLE
                        tvError.visibility = View.GONE
                        tvWelcome.visibility = View.GONE
                        tvEmptyGuide.visibility = View.GONE
                    }
                    PlaybackState.READY -> {
                        progressBar.visibility = View.GONE
                        tvError.visibility = View.GONE
                        tvWelcome.visibility = View.GONE
                        tvEmptyGuide.visibility = View.GONE
                    }
                    PlaybackState.ERROR -> {
                        progressBar.visibility = View.GONE
                        tvError.visibility = View.VISIBLE
                        tvWelcome.visibility = View.GONE
                        tvEmptyGuide.visibility = View.GONE
                    }
                    PlaybackState.IDLE -> {
                        progressBar.visibility = View.GONE
                        if (viewModel.state.value.channels.isEmpty()) {
                            tvWelcome.visibility = View.GONE
                            tvEmptyGuide.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            tvPlayer.errorMessage.collect { msg ->
                if (msg != null) {
                    tvError.text = "播放失败: $msg"
                } else {
                    tvError.text = resources.getString(R.string.error_tip)
                }
            }
        }
    }

    private fun updatePlayer(state: PlayerUiState) {
        val channel = state.currentChannel
        if (state.showOsd) {
            osdLayout.visibility = View.VISIBLE
            osdHandler.removeCallbacksAndMessages(null)
            osdHandler.postDelayed({ osdLayout.visibility = View.GONE }, OSD_TIMEOUT_MS)
        }
    }

    private fun updateChannelInfo(state: PlayerUiState) {
        val channel = state.currentChannel
        if (channel != null) {
            tvChannelName.text = channel.name
            tvCategory.text = channel.category
            osdLayout.visibility = View.VISIBLE
        } else {
            osdLayout.visibility = View.GONE
        }
    }

    private fun updateIconFavorite(state: PlayerUiState) {
        val isFav = state.isFavorite
        btnIconFavorite.setImageResource(
            if (isFav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
        )
    }

    private fun updateEmptyState(state: PlayerUiState) {
        if (state.channels.isEmpty() && state.channelsLoaded) {
            tvWelcome.visibility = View.GONE
            tvEmptyGuide.visibility = View.VISIBLE
            categoryOverlay.visibility = View.GONE
            btnIconFavorite.visibility = View.GONE
        } else {
            tvEmptyGuide.visibility = View.GONE
            btnIconFavorite.visibility = View.VISIBLE
        }
    }

    private fun updateCategoryOverlay(state: PlayerUiState) {
        val isCategorySelect = state.screenStack.lastOrNull() == Screen.CATEGORY_SELECT
        categoryOverlay.visibility = if (isCategorySelect) View.VISIBLE else View.GONE

        if (isCategorySelect) {
            categoryTabs.removeAllViews()
            state.allCategories.forEachIndexed { index, cat ->
                val isSelected = index == state.selectedCategoryIndex
                val tab = TextView(requireContext()).apply {
                    text = cat
                    textSize = 16f
                    setPadding(28, 14, 28, 14)
                    isFocusable = true
                    isClickable = true
                    setOnClickListener { viewModel.selectCategory(index) }
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) viewModel.selectCategory(index)
                    }
                    if (isSelected) {
                        setTextColor(resources.getColor(R.color.white, null))
                        setBackgroundColor(resources.getColor(R.color.accent, null))
                    } else {
                        setTextColor(resources.getColor(R.color.text_secondary, null))
                        setBackgroundColor(resources.getColor(R.color.card_background, null))
                    }
                }
                categoryTabs.addView(tab)
            }

            categoryChannelList.removeAllViews()
            val playingId = state.currentChannel?.id
            state.categoryChannels.forEachIndexed { index, channel ->
                val isPlaying = channel.id == playingId
                val isFocused = index == state.selectedChannelIndex
                val item = TextView(requireContext()).apply {
                    text = channel.name
                    textSize = 18f
                    setPadding(20, 12, 20, 12)
                    isFocusable = true
                    isClickable = true
                    setOnClickListener { viewModel.focusCategoryChannel(index) }
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) viewModel.focusCategoryChannel(index)
                    }
                    if (isPlaying) {
                        setTextColor(resources.getColor(R.color.white, null))
                        setBackgroundResource(R.drawable.focus_bg_playing)
                    } else if (isFocused) {
                        setTextColor(resources.getColor(R.color.white, null))
                        setBackgroundResource(R.drawable.focus_bg_selected)
                    } else {
                        setTextColor(resources.getColor(R.color.text_secondary, null))
                        setBackgroundColor(0)
                    }
                }
                categoryChannelList.addView(item)
            }
        }
    }

    private fun updateExitConfirm(state: PlayerUiState) {
        if (state.showExitConfirm) {
            tvExitConfirm.visibility = View.VISIBLE
            exitConfirmRunnable?.let { exitHandler.removeCallbacks(it) }
            exitConfirmRunnable = Runnable {
                tvExitConfirm.visibility = View.GONE
                viewModel.dismissExitConfirm()
            }
            exitHandler.postDelayed(exitConfirmRunnable!!, 3000L)
        } else {
            tvExitConfirm.visibility = View.GONE
            exitConfirmRunnable?.let { exitHandler.removeCallbacks(it) }
        }
    }

    private fun setupTouch() {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isIconFocus && viewModel.state.value.screenStack.lastOrNull() == Screen.PLAYER) {
                    enterIconSelect()
                }
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                if (abs(diffY) > 100) {
                    if (diffY < 0) viewModel.nextChannel()
                    else viewModel.previousChannel()
                    showOsdTemp()
                    return true
                }
                return false
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                enterIconSelect()
                return true
            }
        })
        view?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val state = viewModel.state.value
        val currentScreen = state.screenStack.lastOrNull() ?: Screen.PLAYER

        if (isIconFocus) return handleIconKey(keyCode)

        return when (currentScreen) {
            Screen.CATEGORY_SELECT -> handleCategoryKey(keyCode)
            Screen.PLAYER -> handlePlayerKey(keyCode)
            else -> false
        }
    }

    private fun handlePlayerKey(keyCode: Int): Boolean {
        val state = viewModel.state.value
        if (state.channels.isEmpty()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MENU) {
                enterIconSelect()
                return true
            }
            return false
        }
        if (state.showExitConfirm) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                requireActivity().finish()
                return true
            }
            viewModel.dismissExitConfirm()
            return true
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.previousChannel()
                showOsdTemp()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.nextChannel()
                showOsdTemp()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                viewModel.enterCategorySelect()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                enterIconSelect()
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                enterIconSelect()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (viewModel.state.value.showExitConfirm) {
                    requireActivity().finish()
                } else {
                    viewModel.navigateBack()
                }
                true
            }
            else -> false
        }
    }

    private fun enterIconSelect() {
        if (viewModel.state.value.channels.isEmpty()) {
            (activity as? MainActivity)?.showSettings()
            return
        }
        isIconFocus = true
        iconSelectedIndex = 0
        iconPanel.visibility = View.VISIBLE
        iconPanel.alpha = 1.0f
        btnIconSettings.requestFocus()
    }

    private fun exitIconSelect() {
        isIconFocus = false
        iconPanel.visibility = View.GONE
        view?.requestFocus()
    }

    private fun handleIconKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                iconSelectedIndex = (iconSelectedIndex - 1 + 3) % 3
                focusIconItem(iconSelectedIndex)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                iconSelectedIndex = (iconSelectedIndex + 1) % 3
                focusIconItem(iconSelectedIndex)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                onIconAction(iconSelectedIndex)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> {
                exitIconSelect()
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                true
            }
            else -> false
        }
    }

    private fun focusIconItem(index: Int) {
        when (index) {
            0 -> btnIconSettings.requestFocus()
            1 -> btnIconEdit.requestFocus()
            2 -> btnIconFavorite.requestFocus()
        }
    }

    private fun onIconAction(index: Int) {
        when (index) {
            0 -> {
                (activity as? MainActivity)?.showSettings()
                isIconFocus = false
            }
            1 -> {
                (activity as? MainActivity)?.showChannelEdit()
                isIconFocus = false
            }
            2 -> {
                val state = viewModel.state.value
                val before = state.isFavorite
                viewModel.toggleFavorite()
                Toast.makeText(
                    requireContext(),
                    if (!before) "已收藏: ${state.currentChannel?.name ?: ""}" else "已取消收藏: ${state.currentChannel?.name ?: ""}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleCategoryKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val newIdx = viewModel.state.value.selectedCategoryIndex - 1
                if (newIdx >= 0) viewModel.selectCategory(newIdx)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val cats = viewModel.state.value.allCategories
                val newIdx = viewModel.state.value.selectedCategoryIndex + 1
                if (newIdx < cats.size) viewModel.selectCategory(newIdx)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                val newIdx = viewModel.state.value.selectedChannelIndex - 1
                if (newIdx >= 0) viewModel.focusCategoryChannel(newIdx)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val chs = viewModel.state.value.categoryChannels
                val newIdx = viewModel.state.value.selectedChannelIndex + 1
                if (newIdx < chs.size) viewModel.focusCategoryChannel(newIdx)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                viewModel.confirmCategorySelect()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                viewModel.cancelCategorySelect()
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                true
            }
            else -> false
        }
    }

    private fun showOsdTemp() {
        osdLayout.visibility = View.VISIBLE
        osdHandler.removeCallbacksAndMessages(null)
        osdHandler.postDelayed({ osdLayout.visibility = View.GONE }, OSD_TIMEOUT_MS)
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.state.value.videoPaused) {
            tvPlayer.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!viewModel.state.value.videoPaused) {
            tvPlayer.resume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tvPlayer.release()
        osdHandler.removeCallbacksAndMessages(null)
        exitHandler.removeCallbacksAndMessages(null)
    }
}
