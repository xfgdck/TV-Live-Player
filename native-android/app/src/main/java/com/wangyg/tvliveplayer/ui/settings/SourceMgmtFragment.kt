package com.wangyg.tvliveplayer.ui.settings

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wangyg.tvliveplayer.MainActivity
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import com.wangyg.tvliveplayer.domain.usecase.ParseAndImportM3UUseCase
import com.wangyg.tvliveplayer.parser.M3UParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SourceMgmtFragment : Fragment() {

    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var m3uParser: M3UParser

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) readAndParseFile(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_source_mgmt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btn_url).setOnClickListener { showUrlDialog() }
        view.findViewById<TextView>(R.id.btn_file).setOnClickListener {
            filePicker.launch(arrayOf("text/*", "*/*"))
        }
        view.findViewById<TextView>(R.id.btn_manual).setOnClickListener { showManualDialog() }
        view.findViewById<TextView>(R.id.btn_clear).setOnClickListener { showClearDialog() }

        view.findViewById<TextView>(R.id.btn_url).requestFocus()
    }

    private fun showUrlDialog() {
        val input = EditText(requireActivity())
        input.hint = "输入 M3U 链接地址"
        input.setText("https://raw.githubusercontent.com/YanG-1989/m3u/main/Gather.m3u")
        AlertDialog.Builder(requireActivity())
            .setTitle("在线链接解析")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) downloadAndParse(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showManualDialog() {
        val view = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_add_channel, null)
        val etName = view.findViewById<EditText>(R.id.et_channel_name)
        val etUrl = view.findViewById<EditText>(R.id.et_channel_url)
        val etCategory = view.findViewById<EditText>(R.id.et_channel_category)
        val etLogo = view.findViewById<EditText>(R.id.et_channel_logo)

        AlertDialog.Builder(requireActivity())
            .setTitle("手动添加频道")
            .setView(view)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(requireContext(), "频道名称和URL不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    channelRepository.addChannels(listOf(
                        Channel(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            url = url,
                            category = etCategory.text.toString().trim().ifEmpty { "自定义" },
                            logo = etLogo.text.toString().trim().ifEmpty { null }
                        )
                    ))
                    Toast.makeText(requireContext(), "添加成功", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearDialog() {
        AlertDialog.Builder(requireActivity())
            .setTitle("确认清空")
            .setMessage("确定要清空所有节目单吗？此操作不可撤销！")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    channelRepository.clearAllChannels()
                    Toast.makeText(requireContext(), "节目单已清空，请添加新的直播源", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.playerViewModel?.resetToPlayer()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun downloadAndParse(url: String) {
        lifecycleScope.launch {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(url).build()
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val body = response.body?.string() ?: return@launch
                val count = ParseAndImportM3UUseCase(m3uParser, channelRepository)(body, null)
                Toast.makeText(requireContext(), "导入成功！共 $count 个频道", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "导入失败：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readAndParseFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: return@launch
                val count = ParseAndImportM3UUseCase(m3uParser, channelRepository)(text, null)
                Toast.makeText(requireContext(), "导入成功！共 $count 个频道", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "导入失败：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
