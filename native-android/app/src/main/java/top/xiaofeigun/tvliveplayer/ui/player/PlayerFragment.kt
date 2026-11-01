package top.xiaofeigun.tvliveplayer.ui.player

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.PlayerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.player.PlaybackState
import top.xiaofeigun.tvliveplayer.player.TVPlayer
import javax.inject.Inject

/**
 * 播放器宿主界面：负责视频播放状态渲染与按键路由；
 * 选台抽屉、快捷面板、频道菜单与触屏手势分别由独立组件实现。
 */
@AndroidEntryPoint
class PlayerFragment : Fragment() {

    @Inject lateinit var tvPlayer: TVPlayer
    private val viewModel: PlayerViewModel by activityViewModels()

    private lateinit var playerView: PlayerView
    private lateinit var osdLayout: View
    private lateinit var tvChannelName: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var ivPause: ImageView
    private lateinit var tvError: TextView
    private lateinit var tvWelcome: TextView
    private lateinit var tvEmptyGuide: TextView
    private lateinit var tvExitConfirm: TextView
    private lateinit var btnIconFavorite: ImageButton

    private lateinit var categoryDrawer: CategoryDrawer
    private lateinit var iconPanel: IconPanel
    private lateinit var channelMenu: ChannelMenuDialogs
    private lateinit var gestures: PlayerGestures

    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideOsdRunnable = Runnable { osdLayout.visibility = View.GONE }
    private val dismissExitRunnable = Runnable { viewModel.dismissExitConfirm() }

    private var lastPlayedChannelId: String? = null
    private var lastPlayedUrls: List<String> = emptyList()
    private var redirectingToSourceMgmt = false
    private var userPaused = false
    /** 退到后台时已停止拉流，回到前台需重新拉流 */
    private var stoppedInBackground = false
    private var edgeToast: Toast? = null
    private var lastEdgeToastTime = 0L
    private val isTouchDevice: Boolean by lazy {
        !requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerView = view.findViewById(R.id.player_view)
        osdLayout = view.findViewById(R.id.osd_layout)
        tvChannelName = view.findViewById(R.id.tv_channel_name)
        progressBar = view.findViewById(R.id.progress_bar)
        ivPause = view.findViewById(R.id.iv_pause)
        tvError = view.findViewById(R.id.tv_error)
        tvWelcome = view.findViewById(R.id.tv_welcome)
        tvEmptyGuide = view.findViewById(R.id.tv_empty_guide)
        tvExitConfirm = view.findViewById(R.id.tv_exit_confirm)
        btnIconFavorite = view.findViewById(R.id.btn_icon_favorite)

        tvPlayer.initialize()
        playerView.player = tvPlayer.player

        categoryDrawer = CategoryDrawer(
            overlay = view.findViewById(R.id.category_overlay),
            tabsScroll = view.findViewById(R.id.category_tabs_scroll),
            tabs = view.findViewById(R.id.category_tabs),
            channelScroll = view.findViewById(R.id.category_scroll),
            channelList = view.findViewById(R.id.category_channel_list),
            listener = drawerListener
        )
        iconPanel = IconPanel(
            panel = view.findViewById(R.id.icon_panel),
            buttons = listOf(
                view.findViewById<ImageButton>(R.id.btn_icon_settings),
                view.findViewById<ImageButton>(R.id.btn_icon_source),
                btnIconFavorite
            ),
            listener = iconListener
        )
        channelMenu = ChannelMenuDialogs(requireActivity(), viewModel)
        gestures = PlayerGestures(gestureListener).also { it.attach(view, playerView) }

        view.findViewById<Button>(R.id.btn_retry).setOnClickListener {
            viewModel.state.value.currentChannel?.let { tvPlayer.playUrls(it.allUrls) }
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    state.currentChannel?.let { tvChannelName.text = it.name }
                    btnIconFavorite.setImageResource(
                        if (state.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
                    )
                    categoryDrawer.render(state)
                    iconPanel.render(state)
                    renderExitConfirm(state)
                    renderEmptyState(state)
                    syncPlayback(state)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            tvPlayer.playbackState.collect { renderPlaybackState(it) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            tvPlayer.errorMessage.collect { msg ->
                if (msg != null) {
                    tvError.text = if (msg.startsWith(TVPlayer.SWITCHING_PREFIX)) msg else "播放失败: $msg"
                    if (tvPlayer.playbackState.value == PlaybackState.BUFFERING && msg.startsWith(TVPlayer.SWITCHING_PREFIX)) {
                        tvError.visibility = View.VISIBLE
                    }
                } else {
                    tvError.text = resources.getString(R.string.error_tip)
                }
            }
        }
    }

    /** 当前频道变化时开始播放；频道被清空时重置播放器；无频道时引导到源管理 */
    private fun syncPlayback(state: PlayerUiState) {
        val channel = state.currentChannel
        if (channel != null) {
            if (channel.id != lastPlayedChannelId) {
                lastPlayedChannelId = channel.id
                lastPlayedUrls = channel.allUrls
                userPaused = false
                tvError.visibility = View.GONE
                ivPause.visibility = View.GONE
                showOsd()
                tvPlayer.playUrls(channel.allUrls)
            } else if (channel.allUrls != lastPlayedUrls) {
                // 刷新订阅等导致地址列表变化：不打断当前播放，新备用源立即可用
                lastPlayedUrls = channel.allUrls
                tvPlayer.updateUrls(channel.allUrls)
            }
        } else if (lastPlayedChannelId != null) {
            lastPlayedChannelId = null
            lastPlayedUrls = emptyList()
            userPaused = false
            ivPause.visibility = View.GONE
            osdLayout.visibility = View.GONE
            tvPlayer.release()
            tvPlayer.initialize()
            playerView.player = tvPlayer.player
        }

        if (state.channels.isEmpty() && state.channelsLoaded) {
            if (!redirectingToSourceMgmt) {
                redirectingToSourceMgmt = true
                Toast.makeText(context, "暂无频道，请添加直播源", Toast.LENGTH_SHORT).show()
                viewModel.pushPage(Page.SOURCE_MGMT)
            }
        } else if (state.channels.isNotEmpty()) {
            redirectingToSourceMgmt = false
        }
    }

    private fun renderPlaybackState(playbackState: PlaybackState) {
        if (playbackState != PlaybackState.IDLE) {
            tvWelcome.visibility = View.GONE
            tvEmptyGuide.visibility = View.GONE
        }
        when (playbackState) {
            PlaybackState.BUFFERING -> {
                progressBar.visibility = View.VISIBLE
                ivPause.visibility = View.GONE
                val errMsg = tvPlayer.errorMessage.value
                if (errMsg != null && errMsg.startsWith(TVPlayer.SWITCHING_PREFIX)) {
                    tvError.text = errMsg
                    tvError.visibility = View.VISIBLE
                } else {
                    tvError.visibility = View.GONE
                }
            }
            PlaybackState.READY -> {
                progressBar.visibility = View.GONE
                if (tvPlayer.isPlaying()) ivPause.visibility = View.GONE
                tvError.visibility = View.GONE
            }
            PlaybackState.ERROR -> {
                progressBar.visibility = View.GONE
                ivPause.visibility = View.GONE
                tvError.visibility = View.VISIBLE
            }
            PlaybackState.IDLE -> {
                progressBar.visibility = View.GONE
                // 有频道时 IDLE 为初始态，保持暂停图标隐藏
                if (viewModel.state.value.channels.isEmpty()) {
                    ivPause.visibility = View.GONE
                    tvError.visibility = View.GONE
                    tvWelcome.visibility = View.GONE
                    tvEmptyGuide.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showOsd() {
        uiHandler.removeCallbacks(hideOsdRunnable)
        osdLayout.visibility = View.VISIBLE
        uiHandler.postDelayed(hideOsdRunnable, 3000L)
    }

    private fun renderEmptyState(state: PlayerUiState) {
        if (state.channels.isEmpty() && state.channelsLoaded) {
            tvWelcome.visibility = View.GONE
            tvEmptyGuide.visibility = View.VISIBLE
            btnIconFavorite.visibility = View.GONE
        } else {
            tvEmptyGuide.visibility = View.GONE
            btnIconFavorite.visibility = View.VISIBLE
        }
    }

    private fun renderExitConfirm(state: PlayerUiState) {
        uiHandler.removeCallbacks(dismissExitRunnable)
        if (state.showExitConfirm) {
            tvExitConfirm.visibility = View.VISIBLE
            uiHandler.postDelayed(dismissExitRunnable, 3000L)
        } else {
            tvExitConfirm.visibility = View.GONE
        }
    }

    // ---------------- 按键路由（返回键由 MainActivity 的 OnBackPressedCallback 统一处理） ----------------

    fun handleKeyDown(keyCode: Int): Boolean {
        if (isTouchDevice && System.currentTimeMillis() - gestures.lastTouchTime < 500) return false
        return when (viewModel.state.value.topPage) {
            Page.ICON -> iconPanel.handleKey(keyCode)
            Page.CATEGORY -> handleCategoryKey(keyCode)
            Page.PLAYER -> handlePlayerKey(keyCode)
            else -> false
        }
    }

    private fun handlePlayerKey(keyCode: Int): Boolean {
        val state = viewModel.state.value
        if (state.channels.isEmpty()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_ALL_APPS
            ) {
                openIconPanel()
                return true
            }
            return false
        }
        if (state.showExitConfirm) {
            viewModel.dismissExitConfirm()
            return true
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.previousChannel()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.nextChannel()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                viewModel.enterCategorySelect()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ALL_APPS -> {
                openIconPanel()
                true
            }
            else -> false
        }
    }

    private fun handleCategoryKey(keyCode: Int): Boolean {
        val state = viewModel.state.value
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val cur = state.selectedCategoryIndex
                if (cur > 0) viewModel.selectCategory(cur - 1) else showEdgeTip("已是第一个分类")
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val cur = state.selectedCategoryIndex
                if (cur < state.allCategories.size - 1) viewModel.selectCategory(cur + 1) else showEdgeTip("已是最后一个分类")
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                val cur = state.selectedChannelIndex
                if (cur > 0) viewModel.focusCategoryChannel(cur - 1) else showEdgeTip("已是第一个频道")
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val cur = state.selectedChannelIndex
                if (cur < state.categoryChannels.size - 1) viewModel.focusCategoryChannel(cur + 1) else showEdgeTip("已是最后一个频道")
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                viewModel.confirmCategorySelect()
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ALL_APPS -> {
                channelMenu.show(state.selectedChannelIndex)
                true
            }
            else -> false
        }
    }

    private fun showEdgeTip(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastEdgeToastTime < 2000L) return
        lastEdgeToastTime = now
        edgeToast?.cancel()
        edgeToast = Toast.makeText(context, message, Toast.LENGTH_SHORT).apply { show() }
    }

    /** 打开快捷面板（不暂停视频）；无频道时直接引导到源管理 */
    private fun openIconPanel() {
        if (viewModel.state.value.channels.isEmpty()) {
            viewModel.pushPage(Page.SOURCE_MGMT)
            return
        }
        viewModel.pushPage(Page.ICON)
    }

    private fun togglePlayPause() {
        if (tvPlayer.isPlaying()) {
            userPaused = true
            tvPlayer.pause()
            ivPause.visibility = View.VISIBLE
        } else {
            userPaused = false
            tvPlayer.resume()
            ivPause.visibility = View.GONE
        }
    }

    private val drawerListener = object : CategoryDrawer.Listener {
        override fun onCategoryClick(index: Int) = viewModel.selectCategory(index)

        override fun onChannelFocused(index: Int) = viewModel.focusCategoryChannel(index)

        override fun onChannelClick(index: Int) {
            // 触屏：首次单击高亮，再次单击同一频道确认换台
            if (index == viewModel.state.value.selectedChannelIndex) {
                viewModel.confirmCategorySelect()
            } else {
                viewModel.focusCategoryChannel(index)
            }
        }

        override fun onChannelLongClick(index: Int) {
            viewModel.focusCategoryChannel(index)
            channelMenu.show(index)
        }
    }

    private val iconListener = object : IconPanel.Listener {
        override fun onIconAction(index: Int) {
            when (index) {
                0 -> viewModel.pushPage(Page.SETTINGS)
                1 -> viewModel.pushPage(Page.SOURCE_MGMT)
                2 -> {
                    val state = viewModel.state.value
                    val name = state.currentChannel?.name.orEmpty()
                    viewModel.toggleFavorite()
                    Toast.makeText(
                        requireContext(),
                        if (!state.isFavorite) "已收藏: $name" else "已取消收藏: $name",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        override fun onClose() = viewModel.popPage()

        override fun onResumeAndClose() {
            userPaused = false
            tvPlayer.resume()
            ivPause.visibility = View.GONE
            viewModel.popPage()
        }
    }

    private val gestureListener = object : PlayerGestures.Listener {
        override fun isPlayerLayerActive() = viewModel.state.value.topPage == Page.PLAYER

        override fun onSingleTap() = viewModel.enterCategorySelect()

        override fun onDoubleTap() = togglePlayPause()

        override fun onLongPress() {
            if (viewModel.state.value.channels.isNotEmpty()) openIconPanel()
        }

        override fun onVerticalSwipe(next: Boolean) {
            if (next) viewModel.nextChannel() else viewModel.previousChannel()
        }
    }

    override fun onStart() {
        super.onStart()
        if (stoppedInBackground) {
            stoppedInBackground = false
            userPaused = false
            ivPause.visibility = View.GONE
            tvPlayer.restart()
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedInBackground = true
        tvPlayer.stop()
    }

    override fun onPause() {
        super.onPause()
        tvPlayer.pause()
    }

    override fun onResume() {
        super.onResume()
        if (!userPaused) tvPlayer.resume()
    }

    override fun onDestroyView() {
        uiHandler.removeCallbacksAndMessages(null)
        playerView.player = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        tvPlayer.release()
    }
}
