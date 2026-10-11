package top.xiaofeigun.tvliveplayer.domain.model

import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val percent: Int) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
