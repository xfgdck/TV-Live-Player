package top.xiaofeigun.tvliveplayer.util

import top.xiaofeigun.tvliveplayer.BuildConfig

/**
 * 全局应用配置与版本信息常量
 * 版本号规则与 GitHub Actions 发布规则保持 100% 一致 (YYYY.MM.DD.HHmm)
 */
object AppConfig {
    /**
     * 当前软件版本名称，例如 "2026.10.11.1816"
     */
    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    /**
     * 当前软件构建版本号 (Unix 秒时间戳)
     */
    val currentVersionCode: Long
        get() = BuildConfig.VERSION_CODE.toLong()
}
