package top.xiaofeigun.tvliveplayer.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.data.preferences.AppPreferences
import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.model.Source
import top.xiaofeigun.tvliveplayer.domain.model.StreamUrl
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.domain.usecase.ImportSubscriptionUseCase
import top.xiaofeigun.tvliveplayer.domain.usecase.ParseAndImportM3UUseCase
import top.xiaofeigun.tvliveplayer.ui.player.PlayerViewModel
import top.xiaofeigun.tvliveplayer.util.QrScanResult
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SourceMgmtFragment : Fragment() {

    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var parseAndImportM3UUseCase: ParseAndImportM3UUseCase
    @Inject lateinit var importSubscriptionUseCase: ImportSubscriptionUseCase
    @Inject lateinit var appPreferences: AppPreferences

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private var sources: List<Source> = emptyList()

    private lateinit var btnAutoRefresh: TextView
    private lateinit var sourceList: LinearLayout
    private lateinit var tvSourceHeader: TextView
    private lateinit var tvSourceEmpty: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_source_mgmt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnAutoRefresh = view.findViewById(R.id.btn_auto_refresh)
        sourceList = view.findViewById(R.id.source_list)
        tvSourceHeader = view.findViewById(R.id.tv_source_header)
        tvSourceEmpty = view.findViewById(R.id.tv_source_empty)
        view.findViewById<TextView>(R.id.btn_qr_scan).setOnClickListener { showQrScanDialog() }
        view.findViewById<TextView>(R.id.btn_url).setOnClickListener { showUrlDialog() }
        renderAutoRefresh(btnAutoRefresh)
        btnAutoRefresh.setOnClickListener {
            val enabled = !appPreferences.isAutoRefreshEnabled()
            appPreferences.setAutoRefreshEnabled(enabled)
            renderAutoRefresh(btnAutoRefresh)
            toast(if (enabled) "已开启：启动时自动更新超过 12 小时未更新的订阅" else "已关闭自动更新")
        }
        view.findViewById<TextView>(R.id.btn_clear).setOnClickListener { showClearDialog() }

        view.findViewById<TextView>(R.id.btn_qr_scan).requestFocus()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                channelRepository.getAllSources().collect { renderSources(it) }
            }
        }
    }

    private fun renderAutoRefresh(button: TextView) {
        button.text = if (appPreferences.isAutoRefreshEnabled()) "启动时自动更新订阅：开" else "启动时自动更新订阅：关"
    }

    private fun closeSelf() {
        playerViewModel.popPage()
    }

    // ---------------- 订阅列表 ----------------

    /** 订阅状态变化（更新、重命名、删除）时重建列表，并尽量保持遥控器焦点位置 */
    private fun renderSources(list: List<Source>) {
        sources = list
        tvSourceHeader.text = "已保存的订阅（${list.size}）"
        tvSourceEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        val focusedIndex = (0 until sourceList.childCount).firstOrNull { sourceList.getChildAt(it).hasFocus() }
        val focusedId = focusedIndex?.let { sourceList.getChildAt(it).tag as? String }
        sourceList.removeAllViews()
        list.forEach { sourceList.addView(createSourceRow(it)) }

        if (focusedIndex != null) {
            val restored = list.indexOfFirst { it.id == focusedId }
                .takeIf { it >= 0 }
                ?: focusedIndex.coerceAtMost(list.size - 1)
            (sourceList.getChildAt(restored) ?: btnAutoRefresh).requestFocus()
        }
    }

    private fun createSourceRow(source: Source): View {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        fun line(text: String, sizeSp: Float, colorRes: Int) = TextView(ctx).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(ContextCompat.getColor(ctx, colorRes))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }

        return LinearLayout(ctx).apply {
            tag = source.id
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.menu_item_bg)
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            isFocusable = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * dp).toInt() }

            addView(line(source.name, 16f, R.color.text_primary))
            addView(line(source.url, 12f, R.color.text_secondary))
            val error = source.lastError
            addView(
                if (error != null) line("更新失败：$error", 12f, R.color.error_color)
                else line(statusText(source), 12f, R.color.text_secondary)
            )
            setOnClickListener { showSourceMenu(source) }
        }
    }

    private fun statusText(source: Source): String =
        if (source.lastUpdated <= 0L) "尚未更新"
        else "${source.channelCount} 个频道 · 更新于 ${TIME_FORMAT.format(Date(source.lastUpdated))}"

    private fun showSourceMenu(source: Source) {
        AlertDialog.Builder(requireActivity())
            .setTitle(source.name)
            .setItems(arrayOf("立即更新", "重命名", "删除订阅")) { _, which ->
                when (which) {
                    0 -> refreshSource(source)
                    1 -> showRenameDialog(source)
                    2 -> confirmDeleteSource(source)
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }

    private fun refreshSource(source: Source) {
        val loadingDialog = showLoadingDialog("正在更新「${source.name}」...")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val count = importSubscriptionUseCase.refresh(source)
                toast("「${source.name}」已更新，共 $count 个频道")
            } catch (e: Exception) {
                toast("更新失败：${e.localizedMessage ?: "网络错误"}")
            } finally {
                loadingDialog.dismiss()
            }
        }
    }

    private fun showRenameDialog(source: Source) {
        val input = EditText(requireActivity()).apply {
            setText(source.name)
            setSelectAllOnFocus(true)
            isSingleLine = true
        }
        AlertDialog.Builder(requireActivity())
            .setTitle("重命名订阅")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = cleanInput(input.text.toString())
                if (name.isNotEmpty() && name != source.name) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        // 以最新状态为准，避免覆盖期间完成的更新结果
                        val latest = sources.find { it.id == source.id } ?: return@launch
                        channelRepository.addSource(latest.copy(name = name))
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
            .also { input.requestFocus() }
    }

    private fun confirmDeleteSource(source: Source) {
        AlertDialog.Builder(requireActivity())
            .setTitle("删除订阅")
            .setMessage("确定删除订阅「${source.name}」吗？\n该订阅导入的播放地址将被移除，仅由该订阅提供的频道（含收藏）会一并删除；其它订阅与手动添加的频道不受影响。")
            .setPositiveButton("确定删除") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    channelRepository.deleteSource(source.id)
                    toast("已删除订阅「${source.name}」")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------------- 导入 ----------------

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
        val name = cleanInput(result.name)
        val url = cleanInput(result.url)
        when {
            name.isEmpty() -> return toast("频道名称不能为空")
            !StreamUrl.isPlayable(url) -> return toast("播放地址无效，仅支持 http / https / rtsp / udp 协议")
        }
        viewLifecycleOwner.lifecycleScope.launch {
            channelRepository.addChannels(listOf(
                Channel(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    category = result.category.ifEmpty { "自定义" },
                    logo = result.logo.ifEmpty { null }
                )
            ))
            toast("频道「$name」添加成功")
            closeSelf()
        }
    }

    private fun importFromUrl(url: String) {
        if (sources.any { it.id == url }) toast("该订阅已存在，将更新其内容")
        runImport { importSubscriptionUseCase(url) }
    }

    /** 统一的导入流程：加载框 → 执行 → 结果提示；成功导入频道后返回上一层 */
    private fun runImport(block: suspend () -> Int) {
        val loadingDialog = showLoadingDialog("正在解析中，请稍候...")
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

    private fun showLoadingDialog(message: String): AlertDialog {
        val progressBar = ProgressBar(requireActivity(), null, android.R.attr.progressBarStyleLarge)
        progressBar.isIndeterminate = true
        val padding = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        progressBar.setPadding(padding, padding / 2, padding, padding / 2)
        return AlertDialog.Builder(requireActivity())
            .setMessage(message)
            .setView(progressBar)
            .setCancelable(false)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext().applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun cleanInput(raw: String): String =
        raw.trim { it <= ' ' || it == '﻿' || it == '​' || it == '‌' || it == '‍' || it == ' ' }

    companion object {
        /** 仅在主线程使用 */
        private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    }
}
