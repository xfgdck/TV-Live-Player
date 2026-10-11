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
    val isFavorite: Boolean = false
) {
    val allUrls: List<String> get() = listOf(url) + backupUrls
}
