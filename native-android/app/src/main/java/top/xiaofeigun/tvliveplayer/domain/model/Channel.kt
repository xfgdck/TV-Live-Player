package top.xiaofeigun.tvliveplayer.domain.model

data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val backupUrls: List<String> = emptyList(),
    val category: String,
    val logo: String? = null,
    val tvgId: String? = null,
    val sortOrder: Int = 0,
    val isFavorite: Boolean = false,
    /** 播放地址 → 所属订阅 id；不在表中的地址为手动/文件导入，刷新订阅时不会被清理 */
    val urlSources: Map<String, String> = emptyMap()
) {
    val allUrls: List<String> get() = listOf(url) + backupUrls
}
