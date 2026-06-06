package top.xiaofeigun.tvliveplayer

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import top.xiaofeigun.tvliveplayer.data.preferences.AppPreferences
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.ui.player.Page
import top.xiaofeigun.tvliveplayer.ui.player.PlayerFragment
import top.xiaofeigun.tvliveplayer.ui.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var appPreferences: AppPreferences

    val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        if (appPreferences.isFirstLaunch()) {
            lifecycleScope.launch {
                channelRepository.resetToDefaults()
                appPreferences.setFirstLaunchDone()
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PlayerFragment(), "player")
                .commit()
        }
    }

    fun showSettings() {
        val fragment = top.xiaofeigun.tvliveplayer.ui.settings.SettingsFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, "settings")
            .addToBackStack("settings")
            .commit()
    }

    fun showSourceMgmt() {
        val fragment = top.xiaofeigun.tvliveplayer.ui.settings.SourceMgmtFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, "source_mgmt")
            .addToBackStack("source_mgmt")
            .commit()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            val navStack = playerViewModel.state.value.navStack

            if (navStack.size <= 1) {
                // Level 0 — delegate to PlayerFragment for exit confirm, or handle FragmentManager
                if (currentFragment is PlayerFragment) {
                    return currentFragment.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
                }
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return true
                }
                finish()
                return true
            }

            // Level 1+: pop navStack one level at a time
            val last = navStack.last()
            playerViewModel.popPage()
            when (last) {
                Page.SETTINGS, Page.SOURCE_MGMT, Page.UPDATE -> {
                    supportFragmentManager.popBackStack()
                }
                else -> {
                    // ICON, CATEGORY — PlayerFragment observes navStack and hides overlay
                }
            }
            return true
        }

        if (currentFragment is PlayerFragment) {
            return currentFragment.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
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
}
