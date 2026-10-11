package top.xiaofeigun.tvliveplayer.data.remote.model

import com.google.gson.annotations.SerializedName

data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("assets") val assets: List<GitHubAssetDto>? = null
)

data class GitHubAssetDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("browser_download_url") val downloadUrl: String? = null
)
