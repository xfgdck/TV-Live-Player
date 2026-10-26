package top.xiaofeigun.tvliveplayer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.viewModels
import top.xiaofeigun.tvliveplayer.data.preferences.AppPreferences
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.ui.player.Page
import top.xiaofeigun.tvliveplayer.ui.player.PlayerFragment
import top.xiaofeigun.tvliveplayer.ui.player.PlayerViewModel
import top.xiaofeigun.tvliveplayer.ui.settings.SettingsFragment
import top.xiaofeigun.tvliveplayer.ui.settings.SourceMgmtFragment
import top.xiaofeigun.tvliveplayer.ui.settings.UpdateFragment
import top.xiaofeigun.tvliveplayer.util.ChannelBackupHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var channelBackupHelper: ChannelBackupHelper

    private val playerViewModel: PlayerViewModel by viewModels()
    private val isTouchDevice: Boolean by lazy {
        !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        lifecycleScope.launch {
            if (appPreferences.isFirstLaunch()) {
                channelRepository.resetToDefaults()
                appPreferences.setFirstLaunchDone()
                channelBackupHelper.saveBackup(channelRepository.exportData())
            } else {
                // 非首次启动（如应用覆盖更新后），检查数据库自愈恢复
                val channels = channelRepository.getAllChannels().first()
                if (channels.isEmpty()) {
                    val backup = channelBackupHelper.loadBackup()
                    if (!backup.isNullOrBlank()) {
                        channelRepository.importData(backup)
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            // 同步提交，确保播放器始终位于 Fragment 层级最底部
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PlayerFragment(), PLAYER_TAG)
                .commitNow()
            handleIntent(intent)
        }

        // 遥控器返回、手势返回（含 Android 16 预测性返回）统一由此处理
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (playerViewModel.onBack()) finish()
            }
        })

        val btnBack = findViewById<TextView>(R.id.btn_global_back)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.state.map { it.navStack }.distinctUntilChanged().collect { navStack ->
                    syncFragmentPages(navStack)
                    btnBack.visibility = if (isTouchDevice && navStack.size > 1) View.VISIBLE else View.GONE
                }
            }
        }
    }

    /** navStack 是唯一导航状态来源：按栈内容挂载 / 移除对应的页面 Fragment */
    private fun syncFragmentPages(navStack: List<Page>) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        var changed = false
        Page.entries.filter { it.isFragmentPage && it !in navStack }.forEach { page ->
            fm.findFragmentByTag(page.name)?.let {
                tx.remove(it)
                changed = true
            }
        }
        navStack.filter { it.isFragmentPage }.forEach { page ->
            if (fm.findFragmentByTag(page.name) == null) {
                tx.add(R.id.fragment_container, createPageFragment(page), page.name)
                changed = true
            }
        }
        if (changed) tx.commitNowAllowingStateLoss()
    }

    private fun createPageFragment(page: Page): Fragment = when (page) {
        Page.SETTINGS -> SettingsFragment()
        Page.SOURCE_MGMT -> SourceMgmtFragment()
        Page.UPDATE -> UpdateFragment()
        else -> error("$page is not a fragment page")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.getStringExtra("page") ?: intent?.getStringExtra("open_page")) {
            "update" -> {
                playerViewModel.pushPage(Page.SETTINGS)
                playerViewModel.pushPage(Page.UPDATE)
            }
            "settings" -> playerViewModel.pushPage(Page.SETTINGS)
            "source_mgmt" -> playerViewModel.pushPage(Page.SOURCE_MGMT)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 外接键盘 Esc 等同遥控器返回键
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
            return true
        }
        // 电视端在焦点系统之前拦截播放器层按键；触屏端交由 onKeyDown 兜底
        if (!isTouchDevice && event.action == KeyEvent.ACTION_DOWN && routeToPlayer(event.keyCode)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isTouchDevice && routeToPlayer(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun routeToPlayer(keyCode: Int): Boolean {
        if (keyCode !in PLAYER_KEYS) return false
        if (playerViewModel.state.value.topPage.isFragmentPage) return false
        val fragment = supportFragmentManager.findFragmentByTag(PLAYER_TAG) as? PlayerFragment ?: return false
        return fragment.handleKeyDown(keyCode)
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            try {
                val data = channelRepository.exportData()
                channelBackupHelper.saveBackup(data)
            } catch (_: Exception) {
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    companion object {
        private const val PLAYER_TAG = "player"
        private val PLAYER_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ALL_APPS
        )
    }
}
