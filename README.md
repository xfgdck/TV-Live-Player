
<p align="center">
  <img src="native-android/app/src/main/res/drawable/ic_launcher_foreground.xml" width="120" height="120" alt="TV Live Player">
</p>

<h1 align="center">万能电视直播</h1>
<p align="center"><b>TV Live Player</b></p>

<p align="center">
  <a href="#">
    <img src="https://img.shields.io/badge/Android-5.1%2B-brightgreen" alt="Android 5.1+">
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/Kotlin-2.0+-blue" alt="Kotlin 2.0+">
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/API-22%2B-success" alt="API 22+">
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/Leanback-Android%20TV-blueviolet" alt="Android TV">
  </a>
</p>

---

**万能电视直播** 是一款面向 **Android TV（电视盒子）** 的纯客户端全屏电视直播应用，完全使用 **遥控器操控**。支持 HLS 直播流播放、M3U 订阅、频道管理、备份恢复等完整功能。

> 🏗️ **项目背景**：最初使用 React 19 + Vite 6 + Capacitor 6（WebView）方案开发，但在 Android 6 设备上存在严重的 WebView 兼容性问题（白屏、HLS 播放不稳定、焦点管理差）。后**迁移至原生 Android（Kotlin）**，彻底解决了兼容性问题，并获得了更佳的性能和遥控器导航体验。

## ✨ 特性

| 特性 | 说明 |
|------|------|
| 📺 **HLS 直播** | 基于 ExoPlayer（Media3）原生解码，支持 Android 5.1+ 全版本 |
| 🎮 **遥控器操控** | Leanback 原生焦点管理，6键流畅导航 |
| 📡 **M3U 订阅** | 预设源一键导入 / 在线链接 / 本地文件 / 文本粘贴 |
| 📋 **频道管理** | 搜索、重命名、排序、删除、收藏 |
| 📦 **备份恢复** | JSON 导入导出，数据迁移无忧 |
| 🏠 **全屏沉浸** | 深色主题，大字体，适合客厅远距离观看 |
| 📱 **体积小巧** | APK 仅约 3-5MB |
| 🔄 **自动构建** | GitHub Actions 自动编译发布 |

## 📷 界面预览

> 界面设计基于 Android TV Leanback 原生风格。

- **频道浏览**：左侧分类导航 + 右侧频道卡片网格
- **播放界面**：全屏视频 + 半透明 OSD 信息
- **设置中心**：5 个 Tab 标签页（M3U订阅 / 手动添加 / 频道管理 / 源管理 / 备份恢复）

## 🎮 遥控器按键映射

| 按键 | 浏览模式 | 播放模式 | 设置模式 |
|------|---------|---------|---------|
| **↑ ↓** | 频道列表上下移动 | 切换频道（上下一个） | 选项间移动 |
| **← →** | 切换分类 | 快速切换分类 | Tab 切换 |
| **OK/确认** | 进入播放器 | 显示/隐藏 OSD 信息 | 确认选择 |
| **Menu** | 打开设置中心 | 打开设置中心 | 关闭设置 |
| **Back** | 双击退出应用 | 回到浏览界面 | 关闭设置 |

> OSD 信息（频道名 + 分类）在播放中按 OK 键显示，3 秒自动隐藏。

## 🛠️ 技术栈

| 类别 | 技术选型 | 版本 |
|------|---------|------|
| 开发语言 | Kotlin | 2.0+ |
| 构建工具 | Gradle Kotlin DSL | 8.9 |
| UI 框架 | AndroidX Leanback | 1.2.0 |
| 视频播放 | Media3 ExoPlayer | 1.5.1 |
| 架构模式 | MVVM + Clean Architecture | - |
| 依赖注入 | Hilt | 2.53.1 |
| 本地数据库 | Room | 2.6.1 |
| 网络请求 | OkHttp | 4.12.0 |
| JSON 解析 | Gson | 2.11.0 |
| 图片加载 | Coil | 3.0.4 |
| 异步框架 | Kotlin Coroutines + Flow | 1.9.0 |
| 最低 SDK | API 22 (Android 5.1) | - |
| 目标 SDK | API 34 (Android 14) | - |

## 📁 项目结构

```
TV-Live-Player/
├── native-android/                  # 原生 Android 项目根目录
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/wangyg/tvliveplayer/
│   │   │   │   ├── App.kt                    # Application 入口
│   │   │   │   ├── MainActivity.kt           # 主 Activity
│   │   │   │   ├── domain/                   # 领域层
│   │   │   │   │   ├── model/                # Channel, Source 数据模型
│   │   │   │   │   ├── repository/           # Repository 接口
│   │   │   │   │   └── usecase/              # Use Case
│   │   │   │   ├── data/                     # 数据层
│   │   │   │   │   ├── local/                # Room 数据库
│   │   │   │   │   ├── repository/           # Repository 实现
│   │   │   │   │   └── preferences/          # SharedPreferences
│   │   │   │   ├── ui/                       # UI 层
│   │   │   │   │   ├── browse/               # 频道浏览 Fragment
│   │   │   │   │   ├── player/               # 播放器 Activity
│   │   │   │   │   └── settings/             # 设置中心
│   │   │   │   ├── player/                   # ExoPlayer 封装
│   │   │   │   └── parser/                   # M3U 解析器
│   │   │   └── res/                          # 资源文件
│   │   └── build.gradle.kts                  # 应用级构建配置
│   ├── build.gradle.kts                      # 项目级构建配置
│   ├── settings.gradle.kts                   # 项目设置
│   ├── gradle.properties                     # Gradle 属性
│   └── gradlew / gradlew.bat                 # Gradle 包装器
├── .github/workflows/build-apk.yml           # GitHub Actions CI/CD
├── .gitignore
└── README.md
```

## 🏗️ 架构设计

### 三层 Clean Architecture

依赖方向：**UI → Domain ← Data**（依赖倒置），Domain 层定义 Repository 接口，Data 层实现，UI 层通过 Hilt 注入。

```
┌─────────────────────────────────────┐
│     UI / Presentation Layer         │
│  Activity · Fragment · ViewModel    │
├─────────────────────────────────────┤
│     Domain / UseCase Layer          │
│  Repository 接口 · 业务逻辑 · 模型   │
├─────────────────────────────────────┤
│     Data Layer                      │
│  Room DB · OkHttp · SharedPrefs     │
└─────────────────────────────────────┘
```

### 📊 数据模型

**Channel（频道）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 唯一标识 |
| name | String | 频道名称 |
| url | String | HLS 直播流地址 |
| category | String | 分类（如"CGTN国际"） |
| logo | String? | 台标 URL |
| tvgId | String? | EPG ID |
| sortOrder | Int | 排序序号 |
| isFavorite | Boolean | 是否收藏 |

**Source（M3U 订阅源）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 唯一标识 |
| name | String | 源名称 |
| url | String | M3U 链接地址 |
| isActive | Boolean | 是否启用 |
| createdAt | Long | 创建时间戳 |

### 📱 Android 6 兼容性策略

| 策略 | 说明 |
|------|------|
| **minSdkVersion** | API 22（Android 5.1），Android 6（API 23）完全覆盖 |
| **ExoPlayer** | 基于原生 MediaCodec API，支持 Android 4.4+，不受 WebView 版本影响 |
| **权限模型** | 联网权限安装即授权；文件选择使用 SAF，无需存储权限 |
| **Desugaring** | 启用 coreLibraryDesugaring，Android 6 上可使用 java.time、Stream 等 Java 8+ API |
| **Leanback** | 兼容 Android 5.0+，提供完整 TV 焦点管理 |

## 🚀 快速开始

### 本地构建

1. **环境要求**
   - Android Studio (Ladybug+)
   - JDK 17
   - Android SDK (build-tools 34)

2. **克隆并打开项目**
   ```bash
   git clone https://github.com/your-username/TV-Live-Player.git
   cd TV-Live-Player/native-android
   ```

3. **使用 Android Studio**
   - 打开 `native-android` 目录
   - 等待 Gradle 同步完成
   - 连接电视盒子或启动 Android TV 模拟器
   - 点击运行 ▶️

4. **命令行构建**
   ```bash
   # Debug APK
   ./gradlew assembleDebug
   
   # Release APK (需配置签名)
   ./gradlew assembleRelease
   
   # APK 输出路径
   # app/build/outputs/apk/debug/app-debug.apk
   # app/build/outputs/apk/release/app-release.apk
   ```

### CI/CD (GitHub Actions)

项目已配置 GitHub Actions 自动构建，每次 push 到 `main/master` 分支时会自动编译 APK：

| 触发条件 | 行为 |
|---------|------|
| Push `main/master` | 自动构建 Debug APK |
| 创建标签 `v*` | 构建 Debug + Release APK 并发布 Release |
| 手动触发 | 在 Actions 页面点击 "Run workflow" |

> **Release 签名**：需要在 GitHub Repository Secrets 中配置以下密钥：
> - `KEYSTORE_BASE64`：签名文件 base64 编码
> - `KEY_ALIAS`：别名
> - `KEY_PASSWORD`：密钥密码
> - `STORE_PASSWORD`：存储密码

## 📜 默认频道

应用内置 12 个预设频道，分为 5 个分类：

| 分类 | 频道 |
|------|------|
| CGTN 国际 | CGTN, CGTN Documentary, CGTN Español, CGTN Français, CGTN العربية, CGTN Русский |
| 纪录纪实 | NASA TV |
| 海外新闻 | Newsmax, Al Jazeera English |
| 体育运动 | Red Bull TV |
| 演示测试 | Test 1, Test 2 |

> 所有频道可通过设置中心的「M3U 订阅」功能任意添加和替换。

## 📝 许可证

本项目仅供个人学习和研究使用。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

<p align="center">
  <sub>Built with ❤️ for Android TV</sub>
</p>
