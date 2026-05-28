package com.wangyg.tvliveplayer.ui.menu

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.wangyg.tvliveplayer.MainActivity
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.ui.player.PlayerViewModel
import com.wangyg.tvliveplayer.ui.player.Screen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainMenuDialogFragment : DialogFragment() {

    private val viewModel: PlayerViewModel by viewModels(ownerProducer = { requireActivity() })

    private var selectedIndex = 0
    private val menuCount = 3

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireActivity(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.fragment_main_menu)

        val btnSettings = dialog.findViewById<ImageButton>(R.id.btn_settings)
        val btnChannelEdit = dialog.findViewById<ImageButton>(R.id.btn_channel_edit)
        val btnFavorite = dialog.findViewById<ImageButton>(R.id.btn_favorite)
        val tvChannelInfo = dialog.findViewById<TextView>(R.id.tv_menu_channel_info)

        updateChannelInfo(tvChannelInfo)
        updateFavoriteIcon(btnFavorite)

        btnSettings.setOnClickListener {
            viewModel.navigateTo(Screen.SETTINGS)
            (activity as? MainActivity)?.showSettings()
            dialog.dismiss()
        }
        btnChannelEdit.setOnClickListener {
            viewModel.navigateTo(Screen.CHANNEL_EDIT)
            (activity as? MainActivity)?.showChannelEdit()
            dialog.dismiss()
        }
        btnFavorite.setOnClickListener {
            val state = viewModel.state.value
            val before = state.isFavorite
            viewModel.toggleFavorite()
            val after = !before
            Toast.makeText(
                requireContext(),
                if (after) "已收藏: ${state.currentChannel?.name ?: ""}" else "已取消收藏: ${state.currentChannel?.name ?: ""}",
                Toast.LENGTH_SHORT
            ).show()
            updateFavoriteIcon(btnFavorite)
        }

        btnSettings.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectedIndex = 0
        }
        btnChannelEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectedIndex = 1
        }
        btnFavorite.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectedIndex = 2
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleKey(keyCode)
            }
            true
        }

        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onResume() {
        super.onResume()
        dialog?.findViewById<ImageButton>(R.id.btn_settings)?.requestFocus()
        selectedIndex = 0
        dialog?.findViewById<TextView>(R.id.tv_menu_channel_info)?.let { updateChannelInfo(it) }
        dialog?.findViewById<ImageButton>(R.id.btn_favorite)?.let { updateFavoriteIcon(it) }
    }

    private fun handleKey(keyCode: Int): Boolean {
        val dialog = dialog ?: return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                selectedIndex = (selectedIndex - 1 + menuCount) % menuCount
                updateFocus(dialog)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                selectedIndex = (selectedIndex + 1) % menuCount
                updateFocus(dialog)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when (selectedIndex) {
                    0 -> {
                        viewModel.navigateTo(Screen.SETTINGS)
                        (activity as? MainActivity)?.showSettings()
                        dismiss()
                    }
                    1 -> {
                        viewModel.navigateTo(Screen.CHANNEL_EDIT)
                        (activity as? MainActivity)?.showChannelEdit()
                        dismiss()
                    }
                    2 -> {
                        val state = viewModel.state.value
                        val before = state.isFavorite
                        viewModel.toggleFavorite()
                        val after = !before
                        Toast.makeText(
                            requireContext(),
                            if (after) "已收藏: ${state.currentChannel?.name ?: ""}" else "已取消收藏: ${state.currentChannel?.name ?: ""}",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.findViewById<ImageButton>(R.id.btn_favorite)?.let { updateFavoriteIcon(it) }
                    }
                }
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                viewModel.closeMenu()
                dismiss()
                true
            }
            else -> false
        }
    }

    private fun updateFocus(dialog: Dialog) {
        when (selectedIndex) {
            0 -> dialog.findViewById<ImageButton>(R.id.btn_settings)?.requestFocus()
            1 -> dialog.findViewById<ImageButton>(R.id.btn_channel_edit)?.requestFocus()
            2 -> dialog.findViewById<ImageButton>(R.id.btn_favorite)?.requestFocus()
        }
    }

    private fun updateFavoriteIcon(btn: ImageButton) {
        val isFav = viewModel.state.value.isFavorite
        btn.setImageResource(if (isFav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
    }

    private fun updateChannelInfo(tv: TextView) {
        val state = viewModel.state.value
        val channel = state.currentChannel
        if (channel != null) {
            tv.text = "${channel.name}\n${channel.category}"
            tv.visibility = View.VISIBLE
        } else {
            tv.visibility = View.GONE
        }
    }
}
