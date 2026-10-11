package top.xiaofeigun.tvliveplayer.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.xiaofeigun.tvliveplayer.data.remote.model.GitHubReleaseDto
import top.xiaofeigun.tvliveplayer.domain.model.DownloadState
import top.xiaofeigun.tvliveplayer.domain.model.UpdateInfo
import top.xiaofeigun.tvliveplayer.domain.repository.UpdateRepository
import top.xiaofeigun.tvliveplayer.util.VersionComparator
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class UpdateRepositoryImpl @Inject constructor(
    @param:Named("unsafeOkHttpClient") private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : UpdateRepository {

    companion object {
        private const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/xfgdck/TV-Live-Player/releases/latest"
    }

    override suspend fun checkUpdate(currentVersionName: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "TV-Live-Player")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("检查更新请求失败 (HTTP ${response.code})")
                )
            }

            val bodyString = response.body?.string()
                ?: return@withContext Result.failure(IOException("响应内容为空"))

            val releaseDto = gson.fromJson(bodyString, GitHubReleaseDto::class.java)
                ?: return@withContext Result.failure(IOException("无法解析发布信息"))

            val tagName = releaseDto.tagName ?: ""
            val releaseNotes = releaseDto.body?.trim().orEmpty()
            val releaseTitle = releaseDto.name ?: tagName
            val releasePageUrl = releaseDto.htmlUrl ?: "https://github.com/xfgdck/TV-Live-Player/releases"

            // Look for release APK in assets
            val apkAsset = releaseDto.assets?.find {
                it.name?.endsWith(".apk", ignoreCase = true) == true
            }

            val apkDownloadUrl = apkAsset?.downloadUrl
            val apkName = apkAsset?.name ?: "TV-Live-Player-latest.apk"
            val apkSize = apkAsset?.size ?: 0L

            val hasUpdate = VersionComparator.hasUpdate(currentVersionName, tagName)
            val isNewer = VersionComparator.isNewer(currentVersionName, tagName)

            val updateInfo = UpdateInfo(
                currentVersionName = currentVersionName,
                latestVersionName = tagName,
                hasUpdate = hasUpdate,
                isNewer = isNewer,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                apkDownloadUrl = apkDownloadUrl,
                apkName = apkName,
                apkSize = apkSize,
                releasePageUrl = releasePageUrl
            )
            Result.success(updateInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun downloadApk(downloadUrl: String, destinationFile: File): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0L, 0L, 0))

        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        try {
            destinationFile.parentFile?.mkdirs()
            if (tempFile.exists()) tempFile.delete()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "TV-Live-Player")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadState.Error("下载失败 (HTTP ${response.code})"))
                return@flow
            }

            val responseBody = response.body
            if (responseBody == null) {
                emit(DownloadState.Error("下载响应内容为空"))
                return@flow
            }

            val totalBytes = responseBody.contentLength()
            var bytesDownloaded = 0L
            var lastEmittedPercent = -1
            var lastEmittedTime = 0L

            responseBody.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read

                        val percent = if (totalBytes > 0) {
                            ((bytesDownloaded * 100) / totalBytes).toInt()
                        } else {
                            -1
                        }

                        val now = System.currentTimeMillis()
                        if (percent != lastEmittedPercent || now - lastEmittedTime > 250) {
                            lastEmittedPercent = percent
                            lastEmittedTime = now
                            emit(DownloadState.Downloading(bytesDownloaded, totalBytes, percent))
                        }
                    }
                    output.flush()
                }
            }

            // Rename tempFile to destinationFile
            if (destinationFile.exists()) destinationFile.delete()
            if (tempFile.renameTo(destinationFile)) {
                emit(DownloadState.Success(destinationFile))
            } else {
                emit(DownloadState.Error("文件重命名失败"))
            }
        } catch (e: CancellationException) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            emit(DownloadState.Error(e.localizedMessage ?: "下载出错"))
        }
    }.flowOn(Dispatchers.IO)
}
