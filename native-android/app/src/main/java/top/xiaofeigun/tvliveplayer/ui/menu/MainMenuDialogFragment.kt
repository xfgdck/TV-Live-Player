package top.xiaofeigun.tvliveplayer.ui.menu

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import top.xiaofeigun.tvliveplayer.MainActivity
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.ui.player.Page
import top.xiaofeigun.tvliveplayer.ui.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainMenuDialogFragment : DialogFragment() {

    private val viewModel: PlayerViewModel by viewModels(ownerProducer = { requireActivity() })

    private var selectedIndex = 0
    private val menuCount = 4

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireActivity(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.fragment_main_menu)

        val btnSettings = dialog.findViewById<ImageButton>(R.id.btn_settings)
        val btnSourceMgmt = dialog.findViewById<ImageButton>(R.id.btn_source_mgmt)
        val btnChannelEdit = dialog.findViewById<ImageButton>(R.id.btn_channel_edit)
        val btnFavorite = dialog.findViewById<ImageButton>(R.id.btn_favorite)
        val tvChannelInfo = dialog.findViewById<TextView>(R.id.tv_menu_channel_info)

        updateChannelInfo(tvChannelInfo)
        updateFavoriteIcon(btnFavorite)

        btnSettings.setOnClickListener {
            viewModel.pushPage(Page.SETTINGS)
            (activity as? MainActivity)?.showSettings()
            dialog.dismiss()
        }
        btnSourceMgmt.setOnClickListener {
            viewModel.pushPage(Page.SOURCE_MGMT)
            (activity as? MainActivity)?.showSourceMgmt()
            dialog.dismiss()
        }
        btnChannelEdit.visibility = View.GONE
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
        btnSourceMgmt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectedIndex = 1
        }
        btnChannelEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectedIndex = 2
        }
        btnFavorite.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectedIndex = 3
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

    private fun getMenuItemId(index: Int): Int = when (index) {
        0 -> R.id.btn_settings
        1 -> R.id.btn_source_mgmt
        2 -> R.id.btn_channel_edit
        3 -> R.id.btn_favorite
        else -> R.id.btn_settings
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
                        viewModel.pushPage(Page.SETTINGS)
                        (activity as? MainActivity)?.showSettings()
                        dismiss()
                    }
                    1 -> {
                        viewModel.pushPage(Page.SOURCE_MGMT)
                        (activity as? MainActivity)?.showSourceMgmt()
                        dismiss()
                    }
                    2 -> {
                        dismiss()
                    }
                    3 -> {
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
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ALL_APPS -> {
                viewModel.resetToPlayer()
                dismiss()
                true
            }
            else -> false
        }
    }

    private fun updateFocus(dialog: Dialog) {
        dialog.findViewById<ImageButton>(getMenuItemId(selectedIndex))?.requestFocus()
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
