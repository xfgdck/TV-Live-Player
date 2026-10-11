package top.xiaofeigun.tvliveplayer.domain.repository

import kotlinx.coroutines.flow.Flow
import top.xiaofeigun.tvliveplayer.domain.model.DownloadState
import top.xiaofeigun.tvliveplayer.domain.model.UpdateInfo
import java.io.File

interface UpdateRepository {
    suspend fun checkUpdate(currentVersionName: String): Result<UpdateInfo>
    fun downloadApk(downloadUrl: String, destinationFile: File): Flow<DownloadState>
}
