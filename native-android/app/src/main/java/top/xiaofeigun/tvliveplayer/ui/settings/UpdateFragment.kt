package top.xiaofeigun.tvliveplayer.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.util.AppConfig
import java.util.Locale

@AndroidEntryPoint
class UpdateFragment : Fragment() {

    private val viewModel: UpdateViewModel by viewModels()

    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var progressChecking: ProgressBar
    private lateinit var layoutUpdateDetails: View
    private lateinit var tvLatestVersion: TextView
    private lateinit var tvApkSize: TextView
    private lateinit var scrollChangelog: ScrollView
    private lateinit var tvChangelog: TextView
    private lateinit var layoutDownloadProgress: View
    private lateinit var progressDownload: ProgressBar
    private lateinit var tvDownloadProgress: TextView
    private lateinit var btnAction: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_update, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        observeState()

        // Auto check updates on open
        if (viewModel.uiState.value is UpdateUiState.Idle) {
            viewModel.checkUpdate(AppConfig.currentVersionName)
        }
    }

    private fun initViews(view: View) {
        tvCurrentVersion = view.findViewById(R.id.tv_current_version)
        tvUpdateStatus = view.findViewById(R.id.tv_update_status)
        progressChecking = view.findViewById(R.id.progress_checking)
        layoutUpdateDetails = view.findViewById(R.id.layout_update_details)
        tvLatestVersion = view.findViewById(R.id.tv_latest_version)
        tvApkSize = view.findViewById(R.id.tv_apk_size)
        scrollChangelog = view.findViewById(R.id.scroll_changelog)
        tvChangelog = view.findViewById(R.id.tv_changelog)
        layoutDownloadProgress = view.findViewById(R.id.layout_download_progress)
        progressDownload = view.findViewById(R.id.progress_download)
        tvDownloadProgress = view.findViewById(R.id.tv_download_progress)
        btnAction = view.findViewById(R.id.btn_action)

        tvCurrentVersion.text = "当前版本：v${AppConfig.currentVersionName}"

        btnAction.isFocusable = true
        btnAction.requestFocus()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderState(state)
            }
        }
    }

    private fun renderState(state: UpdateUiState) {
        when (state) {
            is UpdateUiState.Idle -> {
                tvUpdateStatus.text = "点击下方按钮检查最新版本"
                progressChecking.visibility = View.GONE
                layoutUpdateDetails.visibility = View.GONE
                layoutDownloadProgress.visibility = View.GONE
                btnAction.isEnabled = true
                btnAction.text = "检查更新"
                btnAction.setOnClickListener {
                    viewModel.checkUpdate(AppConfig.currentVersionName)
                }
                btnAction.requestFocus()
            }

            is UpdateUiState.Checking -> {
                tvUpdateStatus.text = "正在连接 GitHub 检查新版本..."
                progressChecking.visibility = View.VISIBLE
                layoutUpdateDetails.visibility = View.GONE
                layoutDownloadProgress.visibility = View.GONE
                btnAction.isEnabled = false
                btnAction.text = "检查中..."
            }

            is UpdateUiState.NoUpdate -> {
                tvUpdateStatus.text = "当前已是最新版本 (${state.updateInfo.latestVersionName})"
                progressChecking.visibility = View.GONE
                layoutUpdateDetails.visibility = View.GONE
                layoutDownloadProgress.visibility = View.GONE
                btnAction.isEnabled = true
                btnAction.text = "重新检查"
                btnAction.setOnClickListener {
                    viewModel.checkUpdate(AppConfig.currentVersionName)
                }
                btnAction.requestFocus()
            }

            is UpdateUiState.HasUpdate -> {
                val info = state.updateInfo
                tvUpdateStatus.text = if (info.isNewer) "发现新版本可用！" else "检测到版本不一致 (远程版本可用)"
                progressChecking.visibility = View.GONE
                layoutUpdateDetails.visibility = View.VISIBLE
                tvLatestVersion.text = if (info.isNewer) "新版本：${info.releaseTitle}" else "远程版本：${info.releaseTitle}"
                tvApkSize.text = if (info.apkSize > 0) "大小：${formatFileSize(info.apkSize)}" else ""
                tvChangelog.text = if (info.releaseNotes.isNotBlank()) info.releaseNotes else "暂无更新说明"
                layoutDownloadProgress.visibility = View.GONE

                btnAction.isEnabled = true
                btnAction.text = if (info.isNewer) "立即下载更新" else "下载并安装远程版本"
                btnAction.setOnClickListener {
                    viewModel.startDownload(requireContext(), info)
                }
                btnAction.requestFocus()
            }

            is UpdateUiState.Downloading -> {
                tvUpdateStatus.text = "正在下载更新安装包..."
                progressChecking.visibility = View.GONE
                layoutUpdateDetails.visibility = View.VISIBLE
                layoutDownloadProgress.visibility = View.VISIBLE

                if (state.percent >= 0) {
                    progressDownload.isIndeterminate = false
                    progressDownload.progress = state.percent
                    val currentStr = formatFileSize(state.bytesDownloaded)
                    val totalStr = formatFileSize(state.totalBytes)
                    tvDownloadProgress.text = "正在下载：${state.percent}% ($currentStr / $totalStr)"
                } else {
                    progressDownload.isIndeterminate = true
                    tvDownloadProgress.text = "正在下载：${formatFileSize(state.bytesDownloaded)}"
                }

                btnAction.isEnabled = true
                btnAction.text = "取消下载"
                btnAction.setOnClickListener {
                    viewModel.cancelDownload(state.updateInfo)
                }
            }

            is UpdateUiState.ReadyToInstall -> {
                val info = state.updateInfo
                tvUpdateStatus.text = if (state.autoLaunch) "安装包下载完成，正在启动安装..." else if (info.isNewer) "安装包已就绪，点击立即安装" else "安装包已就绪（提示：若签名不同或降级需先卸载旧版）"
                progressChecking.visibility = View.GONE
                layoutUpdateDetails.visibility = View.VISIBLE
                tvLatestVersion.text = if (info.isNewer) "新版本：${info.releaseTitle}" else "远程版本：${info.releaseTitle}"
                tvApkSize.text = if (info.apkSize > 0) "大小：${formatFileSize(info.apkSize)}" else ""
                tvChangelog.text = if (info.releaseNotes.isNotBlank()) info.releaseNotes else "暂无更新说明"
                layoutDownloadProgress.visibility = View.GONE

                btnAction.isEnabled = true
                btnAction.text = "立即安装"
                btnAction.setOnClickListener {
                    viewModel.installApk(requireContext(), state.apkFile)
                }
                btnAction.requestFocus()

                if (state.autoLaunch) {
                    viewModel.markAutoLaunched()
                    viewModel.installApk(requireContext(), state.apkFile)
                }
            }

            is UpdateUiState.Error -> {
                tvUpdateStatus.text = state.message
                progressChecking.visibility = View.GONE
                layoutDownloadProgress.visibility = View.GONE

                btnAction.isEnabled = true
                btnAction.text = "重试"
                btnAction.setOnClickListener {
                    if (state.lastUpdateInfo != null && state.lastUpdateInfo.apkDownloadUrl != null) {
                        viewModel.startDownload(requireContext(), state.lastUpdateInfo)
                    } else {
                        viewModel.checkUpdate(AppConfig.currentVersionName)
                    }
                }
                btnAction.requestFocus()
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
    }
}
