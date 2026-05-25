package com.wangyg.tvliveplayer.ui.settings

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import com.wangyg.tvliveplayer.parser.M3UParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsDialogFragment : DialogFragment() {

    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var m3uParser: M3UParser

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val items = arrayOf(
            "订阅 M3U",
            "手动添加频道",
            "频道管理",
            "订阅源与分类管理",
            "备份与恢复"
        )
        return AlertDialog.Builder(requireActivity())
            .setTitle("设置中心")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showM3uSubscriptionDialog()
                    1 -> showManualAddDialog()
                    2 -> showChannelManagementDialog()
                    3 -> showSourceManagementDialog()
                    4 -> showBackupRestoreDialog()
                }
            }
            .setNegativeButton("关闭", null)
            .create()
    }

    // Tab 1: M3U订阅
    private fun showM3uSubscriptionDialog() {
        val options = arrayOf("预设源一键导入", "在线链接解析", "本地文件导入", "粘贴M3U文本")
        AlertDialog.Builder(requireActivity())
            .setTitle("订阅 M3U")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importPresetSources()
                    1 -> showUrlInputDialog()
                    2 -> showToast("请通过文件管理器选择 .m3u 文件")
                    3 -> showPasteM3uDialog()
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }

    private fun importPresetSources() {
        val presets = arrayOf(
            "范明明 IPv6",
            "IPTV-Org 中国大陆",
            "IPTV-Org 香港",
            "IPTV-Org 台湾"
        )
        AlertDialog.Builder(requireActivity())
            .setTitle("选择预设源")
            .setItems(presets) { _, which ->
                val url = when (which) {
                    0 -> "https://fanmingming.com/txt?url=https://live.fanmingming.com/tv/m3u/ipv6.m3u"
                    1 -> "https://iptv-org.github.io/iptv/countries/cn.m3u"
                    2 -> "https://iptv-org.github.io/iptv/countries/hk.m3u"
                    3 -> "https://iptv-org.github.io/iptv/countries/tw.m3u"
                    else -> ""
                }
                downloadAndParse(url)
            }
            .setNegativeButton("返回", null)
            .show()
    }

    private fun downloadAndParse(url: String) {
        lifecycleScope.launch {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@launch
                val count = com.wangyg.tvliveplayer.domain.usecase.ParseAndImportM3UUseCase(
                    m3uParser, channelRepository
                )(body, null)
                showToast("导入成功！共 $count 个频道")
            } catch (e: Exception) {
                showToast("导入失败：${e.localizedMessage}")
            }
        }
    }

    private fun showUrlInputDialog() {
        val input = EditText(requireActivity())
        input.hint = "输入 M3U 链接地址"
        AlertDialog.Builder(requireActivity())
            .setTitle("在线链接")
            .setView(input)
            .setPositiveButton("下载") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) downloadAndParse(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPasteM3uDialog() {
        val input = EditText(requireActivity())
        input.hint = "粘贴 M3U 文本内容"
        input.minLines = 5
        AlertDialog.Builder(requireActivity())
            .setTitle("粘贴 M3U")
            .setView(input)
            .setPositiveButton("解析") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    lifecycleScope.launch {
                        val useCase = com.wangyg.tvliveplayer.domain.usecase.ParseAndImportM3UUseCase(
                            m3uParser, channelRepository
                        )
                        val count = useCase(text, null)
                        showToast("解析完成！共 $count 个频道")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // Tab 2: 手动添加频道
    private fun showManualAddDialog() {
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
                    showToast("频道名称和URL不能为空")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    channelRepository.addChannels(listOf(
                        com.wangyg.tvliveplayer.domain.model.Channel(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            url = url,
                            category = etCategory.text.toString().trim().ifEmpty { "自定义" },
                            logo = etLogo.text.toString().trim().ifEmpty { null }
                        )
                    ))
                    showToast("添加成功")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // Tab 3: 频道管理
    private fun showChannelManagementDialog() {
        showToast("频道管理功能：请在主界面选择频道后使用遥控器菜单键操作")
    }

    // Tab 4: 订阅源与分类管理
    private fun showSourceManagementDialog() {
        lifecycleScope.launch {
            try {
                val sources = channelRepository.getAllSources().first()
                if (sources.isEmpty()) {
                    showToast("暂无订阅源")
                    return@launch
                }
                val sourceItems = sources.mapIndexed { index, source ->
                    "${index + 1}. ${source.url}"
                }.toTypedArray()
                AlertDialog.Builder(requireActivity())
                    .setTitle("订阅源管理")
                    .setItems(sourceItems) { _, which ->
                        if (which in sources.indices) {
                            val selected = sources[which]
                            AlertDialog.Builder(requireActivity())
                                .setTitle("删除订阅源")
                                .setMessage("确定要删除此订阅源吗？\n${selected.url}")
                                .setPositiveButton("删除") { _, _ ->
                                    lifecycleScope.launch {
                                        channelRepository.deleteSource(sources[which])
                                        showToast("已删除订阅源")
                                    }
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                    }
                    .setNegativeButton("返回", null)
                    .show()
            } catch (e: Exception) {
                showToast("获取订阅源失败：${e.localizedMessage}")
            }
        }
    }

    // Tab 5: 备份恢复
    private fun showBackupRestoreDialog() {
        val options = arrayOf("导出数据到JSON", "从JSON导入恢复")
        AlertDialog.Builder(requireActivity())
            .setTitle("备份与恢复")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        lifecycleScope.launch {
                            val json = channelRepository.exportData()
                            showToast("数据已导出 (${json.length} 字符)")
                        }
                    }
                    1 -> showToast("请选择JSON文件导入")
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
