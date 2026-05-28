package com.wangyg.tvliveplayer.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wangyg.tvliveplayer.MainActivity
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import com.wangyg.tvliveplayer.ui.player.Screen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject lateinit var channelRepository: ChannelRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btn_add_source).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AddSourceFragment())
                .addToBackStack("add_source")
                .commit()
        }
        view.findViewById<TextView>(R.id.btn_update).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, UpdateFragment())
                .addToBackStack("update")
                .commit()
        }
        view.findViewById<TextView>(R.id.btn_clear).setOnClickListener {
            AlertDialog.Builder(requireActivity())
                .setTitle("确认清空")
                .setMessage("确定要清空所有节目单吗？此操作不可撤销！")
                .setPositiveButton("确定") { _, _ ->
                    lifecycleScope.launch {
                        channelRepository.clearAllChannels()
                        Toast.makeText(requireContext(), "节目单已清空", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.playerViewModel?.resetToPlayer()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        view.findViewById<TextView>(R.id.btn_add_source).requestFocus()
    }
}
