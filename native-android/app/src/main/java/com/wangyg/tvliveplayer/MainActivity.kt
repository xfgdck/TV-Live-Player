package com.wangyg.tvliveplayer

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.wangyg.tvliveplayer.data.preferences.AppPreferences
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import com.wangyg.tvliveplayer.ui.player.PlayerActivity
import com.wangyg.tvliveplayer.ui.settings.SettingsDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    private var backPressedTime = 0L
    private val backPressInterval = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            if (appPreferences.isFirstLaunch()) {
                lifecycleScope.launch {
                    channelRepository.resetToDefaults()
                    appPreferences.setFirstLaunchDone()
                    startPlayer()
                }
            } else {
                startPlayer()
            }
        }
    }

    private fun startPlayer() {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("channel_name", "CGTN")
        startActivity(intent)
    }

    override fun onBackPressed() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < backPressInterval) {
            finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(
                this@MainActivity,
                getString(R.string.exit_confirm),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            SettingsDialogFragment().show(supportFragmentManager, "SettingsDialog")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
