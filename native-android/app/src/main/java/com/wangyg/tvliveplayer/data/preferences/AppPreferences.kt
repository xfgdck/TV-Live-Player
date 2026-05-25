package com.wangyg.tvliveplayer.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastChannelId(): String? {
        return prefs.getString(KEY_LAST_CHANNEL_ID, null)
    }

    fun setLastChannelId(id: String) {
        prefs.edit().putString(KEY_LAST_CHANNEL_ID, id).apply()
    }

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    companion object {
        private const val PREFS_NAME = "tv_live_player_prefs"
        private const val KEY_LAST_CHANNEL_ID = "last_channel_id"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }
}
