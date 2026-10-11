package top.xiaofeigun.tvliveplayer.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.xiaofeigun.tvliveplayer.domain.model.DownloadState
import top.xiaofeigun.tvliveplayer.domain.model.UpdateInfo
import top.xiaofeigun.tvliveplayer.domain.repository.UpdateRepository
import java.io.File
import javax.inject.Inject

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class NoUpdate(val updateInfo: UpdateInfo) : UpdateUiState()
    data class HasUpdate(val updateInfo: UpdateInfo) : UpdateUiState()
    data class Downloading(
        val updateInfo: UpdateInfo,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percent: Int
    ) : UpdateUiState()
    data class ReadyToInstall(
        val updateInfo: UpdateInfo,
        val apkFile: File,
        val autoLaunch: Boolean = false
    ) : UpdateUiState()
    data class Error(val message: String, val lastUpdateInfo: UpdateInfo? = null) : UpdateUiState()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var downloadedApkFile: File? = null

    fun markAutoLaunched() {
        val cur = _uiState.value
        if (cur is UpdateUiState.ReadyToInstall && cur.autoLaunch) {
            _uiState.value = cur.copy(autoLaunch = false)
        }
    }

    fun checkUpdate(currentVersionName: String) {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            val result = updateRepository.checkUpdate(currentVersionName)
            result.onSuccess { info ->
                if (info.hasUpdate) {
                    val destinationDir = context.getExternalFilesDir("downloads") ?: context.cacheDir
                    val destinationFile = File(destinationDir, info.apkName ?: "TV-Live-Player-latest.apk")
                    if (destinationFile.exists() && (info.apkSize <= 0 || destinationFile.length() == info.apkSize)) {
                        downloadedApkFile = destinationFile
                        _uiState.value = UpdateUiState.ReadyToInstall(info, destinationFile, autoLaunch = false)
                    } else {
                        _uiState.value = UpdateUiState.HasUpdate(info)
                    }
                } else {
                    _uiState.value = UpdateUiState.NoUpdate(info)
                }
            }.onFailure { err ->
                _uiState.value = UpdateUiState.Error("检查更新失败: ${err.localizedMessage ?: "网络错误"}")
            }
        }
    }

    fun startDownload(context: Context, updateInfo: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                try {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    android.widget.Toast.makeText(
                        context,
                        "请在设置中开启「允许安装未知应用」权限，开启后返回即可继续更新",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return
                } catch (_: Exception) {
                    // Fallback to continuing download if settings intent not supported
                }
            }
        }

        val downloadUrl = updateInfo.apkDownloadUrl
        if (downloadUrl.isNullOrBlank()) {
            _uiState.value = UpdateUiState.Error("未找到可下载的 APK 安装包", updateInfo)
            return
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val destinationDir = context.getExternalFilesDir("downloads") ?: context.cacheDir
            val destinationFile = File(destinationDir, updateInfo.apkName ?: "TV-Live-Player-latest.apk")
            downloadedApkFile = destinationFile

            updateRepository.downloadApk(downloadUrl, destinationFile).collect { state ->
                when (state) {
                    is DownloadState.Idle -> {}
                    is DownloadState.Downloading -> {
                        _uiState.value = UpdateUiState.Downloading(
                            updateInfo = updateInfo,
                            bytesDownloaded = state.bytesDownloaded,
                            totalBytes = state.totalBytes,
                            percent = state.percent
                        )
                    }
                    is DownloadState.Success -> {
                        _uiState.value = UpdateUiState.ReadyToInstall(
                            updateInfo = updateInfo,
                            apkFile = state.file,
                            autoLaunch = true
                        )
                    }
                    is DownloadState.Error -> {
                        _uiState.value = UpdateUiState.Error("下载失败: ${state.message}", updateInfo)
                    }
                }
            }
        }
    }

    fun cancelDownload(lastUpdateInfo: UpdateInfo?) {
        downloadJob?.cancel()
        downloadJob = null
        downloadedApkFile?.let {
            val tmp = File(it.parentFile, "${it.name}.tmp")
            if (tmp.exists()) tmp.delete()
        }
        if (lastUpdateInfo != null) {
            _uiState.value = UpdateUiState.HasUpdate(lastUpdateInfo)
        } else {
            _uiState.value = UpdateUiState.Idle
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                _uiState.value = UpdateUiState.Error("安装包文件不存在或已损坏，请重新下载")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    try {
                        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                        android.widget.Toast.makeText(
                            context,
                            "请在设置中开启「允许安装未知应用」权限，开启后返回即可安装",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return
                    } catch (_: Exception) {
                        // Fall through to standard view intent if settings intent is unsupported on TV ROM
                    }
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resList = context.packageManager.queryIntentActivities(
                installIntent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resList) {
                val pkg = resolveInfo.activityInfo.packageName
                context.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            _uiState.value = UpdateUiState.Error("启动安装器失败: ${e.localizedMessage ?: "未知错误"}")
        }
    }
}
