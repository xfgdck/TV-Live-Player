package com.wangyg.tvliveplayer

import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import com.wangyg.tvliveplayer.data.preferences.AppPreferences
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
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
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("settings")
            .commit()
    }

    fun showChannelEdit() {
        val fragment = com.wangyg.tvliveplayer.ui.channel.ChannelEditFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("channel_edit")
            .commit()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentFragment is PlayerFragment) {
                return currentFragment.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
            }
            if (supportFragmentManager.backStackEntryCount > 0) {
                playerViewModel.resetToPlayer()
                supportFragmentManager.popBackStack()
                return true
            }
            finish()
            return true
        }

        if (currentFragment is PlayerFragment) {
            return currentFragment.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
        }

        return super.onKeyDown(keyCode, event)
    }
}
