package com.wangyg.tvliveplayer

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import com.wangyg.tvliveplayer.ui.browse.BrowseFragment
import com.wangyg.tvliveplayer.ui.settings.SettingsDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var channelRepository: ChannelRepository

    private var backPressedTime = 0L
    private val backPressInterval = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                .replace(android.R.id.content, BrowseFragment())
                .commit()
        }

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
