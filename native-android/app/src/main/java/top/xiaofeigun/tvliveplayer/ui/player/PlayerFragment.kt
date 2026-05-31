package top.xiaofeigun.tvliveplayer.ui.player

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import top.xiaofeigun.tvliveplayer.MainActivity
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.player.PlaybackState
import top.xiaofeigun.tvliveplayer.player.TVPlayer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class PlayerFragment : Fragment() {

    @Inject lateinit var tvPlayer: TVPlayer
    private val viewModel: PlayerViewModel by viewModels(ownerProducer = { requireActivity() })

    private lateinit var surfaceView: TextureView
    private lateinit var osdLayout: View
    private lateinit var tvChannelName: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var ivPause: ImageView
    private lateinit var tvError: TextView
    private lateinit var btnRetry: Button
    private lateinit var tvWelcome: TextView
    private lateinit var tvEmptyGuide: TextView
    private lateinit var tvExitConfirm: TextView
    private lateinit var categoryOverlay: View
    private lateinit var categoryScroll: ScrollView
    private lateinit var categoryChannelList: LinearLayout
    private lateinit var categoryTabs: LinearLayout
    private lateinit var iconPanel: View
    private lateinit var btnIconSettings: ImageButton
    private lateinit var btnIconSource: ImageButton
    private lateinit var btnIconFavorite: ImageButton

    private val osdHandler = Handler(Looper.getMainLooper())
    private val exitHandler = Handler(Looper.getMainLooper())
    private val OSD_TIMEOUT_MS = 3000L
    private var exitConfirmRunnable: Runnable? = null
    private var lastPlayedUrl: String? = null
    private var redirectingToSourceMgmt = false

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
        progressBar = view.findViewById(R.id.progress_bar)
        ivPause = view.findViewById(R.id.iv_pause)
        tvError = view.findViewById(R.id.tv_error)
        btnRetry = view.findViewById(R.id.btn_retry)
        tvWelcome = view.findViewById(R.id.tv_welcome)
        tvEmptyGuide = view.findViewById(R.id.tv_empty_guide)
        tvExitConfirm = view.findViewById(R.id.tv_exit_confirm)
        categoryOverlay = view.findViewById(R.id.category_overlay)
        categoryScroll = view.findViewById(R.id.category_scroll)
        categoryChannelList = view.findViewById(R.id.category_channel_list)
        categoryTabs = view.findViewById(R.id.category_tabs)
        iconPanel = view.findViewById(R.id.icon_panel)
        btnIconSettings = view.findViewById(R.id.btn_icon_settings)
        btnIconSource = view.findViewById(R.id.btn_icon_source)
        btnIconFavorite = view.findViewById(R.id.btn_icon_favorite)

        tvPlayer.initialize(surfaceView)
        setupTouch()

        btnIconSettings.setOnClickListener { onIconAction(0) }
        btnIconSource.setOnClickListener { onIconAction(1) }
        btnIconFavorite.setOnClickListener { onIconAction(2) }

        btnIconSettings.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) iconSelectedIndex = 0
        }
        btnIconSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) iconSelectedIndex = 1
        }
        btnIconFavorite.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) iconSelectedIndex = 2
        }

        btnRetry.setOnClickListener {
            viewModel.state.value.currentChannel?.let { tvPlayer.playUrls(it.allUrls) }
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
                    updateIconPanel(state)
                    updateExitConfirm(state)
                    updateEmptyState(state)
                    val channel = state.currentChannel
                    if (channel != null) {
                        val playUrl = channel.url
                        if (playUrl != lastPlayedUrl) {
                            lastPlayedUrl = playUrl
                            tvError.visibility = View.GONE
                            ivPause.visibility = View.GONE
                            tvPlayer.playUrls(channel.allUrls)
                        }
                    } else if (lastPlayedUrl != null) {
                        lastPlayedUrl = null
                        ivPause.visibility = View.GONE
                        tvPlayer.release()
                        tvPlayer.initialize(surfaceView)
                    }
                    if (state.channels.isEmpty() && state.channelsLoaded && !redirectingToSourceMgmt) {
                        redirectingToSourceMgmt = true
                        Toast.makeText(context, "暂无频道，请添加直播源", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.showSourceMgmt()
                    } else if (state.channels.isNotEmpty()) {
                        redirectingToSourceMgmt = false
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            tvPlayer.playbackState.collect { playbackState ->
                when (playbackState) {
                    PlaybackState.BUFFERING -> {
                        progressBar.visibility = View.VISIBLE
                        ivPause.visibility = View.GONE
                        tvError.visibility = View.GONE
                        tvWelcome.visibility = View.GONE
                        tvEmptyGuide.visibility = View.GONE
                    }
                    PlaybackState.READY -> {
                        progressBar.visibility = View.GONE
                        if (tvPlayer.isPlaying()) {
                            ivPause.visibility = View.GONE
                        }
                        tvError.visibility = View.GONE
                        tvWelcome.visibility = View.GONE
                        tvEmptyGuide.visibility = View.GONE
                    }
                    PlaybackState.ERROR -> {
                        progressBar.visibility = View.GONE
                        ivPause.visibility = View.GONE
                        tvError.visibility = View.VISIBLE
                        tvWelcome.visibility = View.GONE
                        tvEmptyGuide.visibility = View.GONE
                    }
                    PlaybackState.IDLE -> {
                        progressBar.visibility = View.GONE
                        if (viewModel.state.value.channels.isEmpty()) {
                            ivPause.visibility = View.GONE
                            tvError.visibility = View.GONE
                            tvWelcome.visibility = View.GONE
                            tvEmptyGuide.visibility = View.VISIBLE
                        } else {
                            // IDLE with channels means initial state — leave pause icon hidden
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
        if (state.showOsd) {
            osdLayout.visibility = View.VISIBLE
            if (state.navStack.lastOrNull() == Page.PLAYER) {
                osdHandler.removeCallbacksAndMessages(null)
                osdHandler.postDelayed({ osdLayout.visibility = View.GONE }, OSD_TIMEOUT_MS)
            }
        }
    }

    private fun updateChannelInfo(state: PlayerUiState) {
        if (state.currentChannel != null) {
            tvChannelName.text = state.currentChannel.name
            osdLayout.visibility = View.VISIBLE
            if (state.navStack.lastOrNull() == Page.PLAYER) {
                osdHandler.removeCallbacksAndMessages(null)
                osdHandler.postDelayed({ osdLayout.visibility = View.GONE }, OSD_TIMEOUT_MS)
            }
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
        val isCategorySelect = state.navStack.lastOrNull() == Page.CATEGORY
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
            categoryScroll.post {
                val focused = categoryChannelList.getChildAt(state.selectedChannelIndex) ?: return@post
                val scrollY = categoryScroll.scrollY
                val viewHeight = categoryScroll.height
                if (focused.top < scrollY) {
                    categoryScroll.smoothScrollTo(0, focused.top)
                } else if (focused.bottom > scrollY + viewHeight) {
                    categoryScroll.smoothScrollTo(0, focused.bottom - viewHeight)
                }
            }
        }
    }

    private fun updateIconPanel(state: PlayerUiState) {
        val shouldShow = state.navStack.lastOrNull() == Page.ICON
        if (shouldShow && iconPanel.visibility != View.VISIBLE) {
            iconPanel.visibility = View.VISIBLE
            iconPanel.alpha = 1.0f
            iconSelectedIndex = -1
            view?.requestFocus()
        } else if (!shouldShow && iconPanel.visibility == View.VISIBLE) {
            iconPanel.visibility = View.GONE
            iconSelectedIndex = -1
            view?.requestFocus()
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
                if (viewModel.state.value.navStack.lastOrNull() == Page.PLAYER) {
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
            override fun onLongPress(e: MotionEvent) {
                if (viewModel.state.value.navStack.lastOrNull() == Page.PLAYER) {
                    if (viewModel.state.value.channels.isNotEmpty()) {
                        enterIconSelect()
                    }
                }
            }
        })
        view?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val state = viewModel.state.value
        return when (state.navStack.lastOrNull()) {
            Page.ICON -> handleIconKey(keyCode)
            Page.CATEGORY -> handleCategoryKey(keyCode)
            Page.PLAYER -> handlePlayerKey(keyCode)
            else -> false
        }
    }

    private fun handlePlayerKey(keyCode: Int): Boolean {
        val state = viewModel.state.value
        if (state.channels.isEmpty()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_ALL_APPS) {
                enterIconSelect()
                return true
            }
            return false
        }
        if (state.showExitConfirm) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
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
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                viewModel.enterCategorySelect()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!tvPlayer.isPlaying()) {
                    tvPlayer.resume()
                    ivPause.visibility = View.GONE
                } else {
                    enterIconSelect(pause = true)
                }
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ALL_APPS -> {
                enterIconSelect(focusButton = true)
                true
            }

            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                viewModel.popPage()
                true
            }
            else -> false
        }
    }

    private fun enterIconSelect(pause: Boolean = false, focusButton: Boolean = false) {
        if (viewModel.state.value.channels.isEmpty()) {
            (activity as? MainActivity)?.showSourceMgmt()
            return
        }
        if (pause && tvPlayer.isPlaying()) {
            tvPlayer.pause()
            ivPause.visibility = View.VISIBLE
        }
        viewModel.pushPage(Page.ICON)
        iconPanel.visibility = View.VISIBLE
        iconPanel.alpha = 1.0f
        if (focusButton) {
            iconSelectedIndex = 0
            btnIconSettings.requestFocus()
        } else {
            iconSelectedIndex = -1
            view?.requestFocus()
        }
    }

    private fun exitIconSelect() {
        viewModel.popPage()
        iconSelectedIndex = -1
        iconPanel.visibility = View.GONE
        view?.requestFocus()
    }

    private fun handleIconKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (iconSelectedIndex < 0) {
                    iconSelectedIndex = 0
                    focusIconItem(0)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (iconSelectedIndex >= 0) {
                    iconSelectedIndex = -1
                    view?.requestFocus()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (iconSelectedIndex >= 0) {
                    iconSelectedIndex = (iconSelectedIndex - 1 + 3) % 3
                    focusIconItem(iconSelectedIndex)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (iconSelectedIndex >= 0) {
                    iconSelectedIndex = (iconSelectedIndex + 1) % 3
                    focusIconItem(iconSelectedIndex)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (iconSelectedIndex >= 0) {
                    onIconAction(iconSelectedIndex)
                } else {
                    // No button focused: resume video and exit menu
                    tvPlayer.resume()
                    ivPause.visibility = View.GONE
                    exitIconSelect()
                }
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ALL_APPS -> {
                exitIconSelect()
                true
            }
            else -> false
        }
    }

    private fun focusIconItem(index: Int) {
        when (index) {
            0 -> btnIconSettings.requestFocus()
            1 -> btnIconSource.requestFocus()
            2 -> btnIconFavorite.requestFocus()
        }
    }

    private fun onIconAction(index: Int) {
        when (index) {
            0 -> {
                viewModel.pushPage(Page.SETTINGS)
                (activity as? MainActivity)?.showSettings()
            }
            1 -> {
                viewModel.pushPage(Page.SOURCE_MGMT)
                (activity as? MainActivity)?.showSourceMgmt()
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
                val cur = viewModel.state.value.selectedCategoryIndex
                if (cur > 0) {
                    viewModel.selectCategory(cur - 1)
                } else {
                    Toast.makeText(context, "已是第一个分类", Toast.LENGTH_SHORT).show()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val cats = viewModel.state.value.allCategories
                val cur = viewModel.state.value.selectedCategoryIndex
                if (cur < cats.size - 1) {
                    viewModel.selectCategory(cur + 1)
                } else {
                    Toast.makeText(context, "已是最后一个分类", Toast.LENGTH_SHORT).show()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                val cur = viewModel.state.value.selectedChannelIndex
                if (cur > 0) {
                    viewModel.focusCategoryChannel(cur - 1)
                } else {
                    Toast.makeText(context, "已是第一个频道", Toast.LENGTH_SHORT).show()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val chs = viewModel.state.value.categoryChannels
                val cur = viewModel.state.value.selectedChannelIndex
                if (cur < chs.size - 1) {
                    viewModel.focusCategoryChannel(cur + 1)
                } else {
                    Toast.makeText(context, "已是最后一个频道", Toast.LENGTH_SHORT).show()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                viewModel.confirmCategorySelect()
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ALL_APPS -> {
                showCategoryChannelMenu()
                true
            }
            else -> false
        }
    }

    private fun showCategoryChannelMenu() {
        val state = viewModel.state.value
        val idx = state.selectedChannelIndex
        val chs = state.categoryChannels
        if (idx < 0 || idx >= chs.size) return
        val channel = chs[idx]
        val curCat = state.allCategories.getOrNull(state.selectedCategoryIndex) ?: return

        if (curCat == ChannelRepository.FAVORITE_CATEGORY) {
            AlertDialog.Builder(requireActivity())
                .setTitle(channel.name)
                .setItems(arrayOf("取消收藏")) { _, _ ->
                    viewModel.toggleFavorite()
                    Toast.makeText(requireContext(), "已取消收藏", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("返回", null)
                .show()
            return
        }

        val items = mutableListOf("移动到其它分类", "删除当前频道", "清空当前分类")
        val availableCats = state.allCategories.filter {
            it != ChannelRepository.FAVORITE_CATEGORY && it != curCat
        }

        AlertDialog.Builder(requireActivity())
            .setTitle(channel.name)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> if (availableCats.isNotEmpty()) {
                        showMoveCategoryDialog(channel, idx, availableCats)
                    } else {
                        Toast.makeText(requireContext(), "无其他分类可移动", Toast.LENGTH_SHORT).show()
                    }
                    1 -> viewModel.deleteCategoryChannel(idx)
                    2 -> showDeleteCategoryConfirm(curCat)
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }

    private fun showMoveCategoryDialog(channel: Channel, channelIndex: Int, categories: List<String>) {
        AlertDialog.Builder(requireActivity())
            .setTitle("移动到")
            .setItems(categories.toTypedArray()) { _, which ->
                if (which < categories.size) {
                    viewModel.moveCategoryChannel(channelIndex, categories[which])
                    Toast.makeText(requireContext(), "已移动到 ${categories[which]}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteCategoryConfirm(category: String) {
        AlertDialog.Builder(requireActivity())
            .setTitle("清空分类")
            .setMessage("确定要清空分类「$category」吗？该分类下的所有频道将被永久删除。")
            .setPositiveButton("确定清空") { _, _ ->
                viewModel.deleteCategory(category)
                Toast.makeText(requireContext(), "分类「$category」已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showOsdTemp() {
        osdLayout.visibility = View.VISIBLE
        if (viewModel.state.value.navStack.lastOrNull() == Page.PLAYER) {
            osdHandler.removeCallbacksAndMessages(null)
            osdHandler.postDelayed({ osdLayout.visibility = View.GONE }, OSD_TIMEOUT_MS)
        }
    }

    private fun togglePlayPause() {
        if (tvPlayer.isPlaying()) {
            tvPlayer.pause()
            ivPause.visibility = View.VISIBLE
        } else {
            tvPlayer.resume()
            ivPause.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.state.value.navStack.lastOrNull() != Page.ICON) {
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
