package top.xiaofeigun.tvliveplayer.domain.model

data class UpdateInfo(
    val currentVersionName: String,
    val latestVersionName: String,
    val hasUpdate: Boolean,
    val isNewer: Boolean = true,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val apkName: String?,
    val apkSize: Long,
    val releasePageUrl: String
)
