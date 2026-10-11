package top.xiaofeigun.tvliveplayer.domain.model

data class Source(
    val id: String,
    val name: String,
    val url: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
