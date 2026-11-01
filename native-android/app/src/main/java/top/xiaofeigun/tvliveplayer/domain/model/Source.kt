package top.xiaofeigun.tvliveplayer.domain.model

data class Source(
    val id: String,
    val name: String,
    val url: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** 最近一次成功更新的时间，0 表示从未成功 */
    val lastUpdated: Long = 0L,
    /** 最近一次成功更新解析出的频道数 */
    val channelCount: Int = 0,
    /** 最近一次更新失败的原因；成功后清空 */
    val lastError: String? = null
)
