package top.xiaofeigun.tvliveplayer.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.domain.usecase.ImportSubscriptionUseCase
import top.xiaofeigun.tvliveplayer.domain.usecase.ParseAndImportM3UUseCase
import top.xiaofeigun.tvliveplayer.ui.player.PlayerViewModel
import top.xiaofeigun.tvliveplayer.util.QrScanResult
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SourceMgmtFragment : Fragment() {

    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var parseAndImportM3UUseCase: ParseAndImportM3UUseCase
    @Inject lateinit var importSubscriptionUseCase: ImportSubscriptionUseCase

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private var sourceCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_source_mgmt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnRefresh = view.findViewById<TextView>(R.id.btn_refresh)
        view.findViewById<TextView>(R.id.btn_qr_scan).setOnClickListener { showQrScanDialog() }
        view.findViewById<TextView>(R.id.btn_url).setOnClickListener { showUrlDialog() }
        btnRefresh.setOnClickListener { refreshSubscriptions() }
        view.findViewById<TextView>(R.id.btn_clear).setOnClickListener { showClearDialog() }

        view.findViewById<TextView>(R.id.btn_qr_scan).requestFocus()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                channelRepository.getAllSources().collect { sources ->
                    sourceCount = sources.size
                    btnRefresh.text = getString(R.string.refresh_sources, sources.size)
                }
            }
        }
    }

    private fun closeSelf() {
        playerViewModel.popPage()
    }

    private fun showUrlDialog() {
        val view = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_input_url, null)
        val input = view.findViewById<EditText>(R.id.et_url)
        AlertDialog.Builder(requireActivity())
            .setTitle("在线链接解析")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val url = cleanInput(input.text.toString())
                if (url.isNotEmpty()) importFromUrl(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showQrScanDialog() {
        val dialog = QrInputDialogFragment()
        dialog.setOnResultReceived { result ->
            when (result.type) {
                "m3u_url" -> {
                    val url = cleanInput(result.url)
                    if (url.isNotEmpty()) importFromUrl(url)
                }
                "channel" -> addChannelFromQr(result)
                "file" -> runImport { parseAndImportM3UUseCase(result.fileContent) }
            }
        }
        dialog.setOnCancel {
            Toast.makeText(requireContext(), "已取消", Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "qr_input")
    }

    private fun addChannelFromQr(result: QrScanResult) {
        val url = cleanInput(result.url)
        viewLifecycleOwner.lifecycleScope.launch {
            channelRepository.addChannels(listOf(
                Channel(
                    id = UUID.randomUUID().toString(),
                    name = result.name.trim(),
                    url = url,
                    category = result.category.ifEmpty { "自定义" },
                    logo = result.logo.ifEmpty { null }
                )
            ))
            toast("频道「${result.name}」添加成功")
            closeSelf()
        }
    }

    private fun importFromUrl(url: String) = runImport { importSubscriptionUseCase(url) }

    /** 统一的导入流程：加载框 → 执行 → 结果提示；成功导入频道后返回上一层 */
    private fun runImport(block: suspend () -> Int) {
        val loadingDialog = showLoadingDialog()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val count = block()
                if (count > 0) {
                    toast("导入成功！共 $count 个频道")
                    closeSelf()
                } else {
                    toast("未解析到任何频道，请检查直播源格式")
                }
            } catch (e: Exception) {
                toast("导入失败：${e.localizedMessage ?: "网络错误"}")
            } finally {
                loadingDialog.dismiss()
            }
        }
    }

    private fun refreshSubscriptions() {
        if (sourceCount == 0) {
            toast("暂无已保存的订阅，请先通过 URL 或扫码导入在线直播源")
            return
        }
        val loadingDialog = showLoadingDialog()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = importSubscriptionUseCase.refreshAll()
                toast(
                    if (result.failed == 0) "已更新 ${result.succeeded} 个订阅，共 ${result.channelCount} 个频道"
                    else "已更新 ${result.succeeded} 个订阅，${result.failed} 个失败"
                )
            } finally {
                loadingDialog.dismiss()
            }
        }
    }

    private fun showClearDialog() {
        AlertDialog.Builder(requireActivity())
            .setTitle("确认清空")
            .setMessage("确定要清空所有节目单与已保存的订阅吗？此操作不可撤销！")
            .setPositiveButton("确定") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    channelRepository.clearAll()
                    toast("节目单已清空，请添加新的直播源")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLoadingDialog(): AlertDialog {
        val progressBar = ProgressBar(requireActivity(), null, android.R.attr.progressBarStyleLarge)
        progressBar.isIndeterminate = true
        val padding = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        progressBar.setPadding(padding, padding / 2, padding, padding / 2)
        return AlertDialog.Builder(requireActivity())
            .setMessage("正在解析中，请稍候...")
            .setView(progressBar)
            .setCancelable(false)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext().applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun cleanInput(raw: String): String =
        raw.trim { it <= ' ' || it == '﻿' || it == '​' || it == '‌' || it == '‍' || it == ' ' }
}
