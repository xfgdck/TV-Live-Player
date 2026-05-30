package com.wangyg.tvliveplayer

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import com.wangyg.tvliveplayer.data.preferences.AppPreferences
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import com.wangyg.tvliveplayer.ui.player.Page
import com.wangyg.tvliveplayer.ui.player.PlayerFragment
import com.wangyg.tvliveplayer.ui.player.PlayerViewModel
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
        val fragment = com.wangyg.tvliveplayer.ui.settings.SettingsFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, "settings")
            .addToBackStack("settings")
            .commit()
    }

    fun showSourceMgmt() {
        val fragment = com.wangyg.tvliveplayer.ui.settings.SourceMgmtFragment()
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
}
