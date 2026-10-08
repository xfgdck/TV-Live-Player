# AGENTS.md - TV-Live-Player 架构规范与 AI 协作准则

> **致接手本项目的 AI Agent**：  
> 本文件是 TV-Live-Player 项目的**唯一最高工程指引、产品设计基准与协作规范**。在阅读需求、生成计划、编写或重构代码前，**务必全文阅读并严格遵守**本文件约定的架构分层、状态机规则、遥控器焦点准则与 Git 提交纪律。

> [!CAUTION]
> **Git 提交与发布控制原则（极其重要）**：  
> 任何 AI Agent **严禁擅自执行** `git commit` 或 `git push` 操作！代码修改和本地验证完成后，**必须等待用户明确下达提交指令**（例如：“提交代码”、“推送到 GitHub”、“commit and push”）后，方可执行 Git 提交与远程推送。

---

## 1. 项目愿景与全局设计约定

- **项目名称**：TV-Live-Player (`TV Player`)
- **包名 (Application ID)**：`top.xiaofeigun.tvliveplayer`
- **目标终端**：Android TV / 电视机顶盒（主），同时兼容 Android 手机/平板触摸屏（辅）。
- **兼容系统**：`minSdk 23` (Android 6.0)，`targetSdk 36`，`compileSdk 36`。通用 APK，兼容 32/64 位处理器。
- **全局业务约定**：
  1. **音量控制**：完全由系统硬件接管，应用内不实现自制音量调节，避免遥控器音量键被应用拦截。
  2. **频道记忆**：每次换台立即持久化保存当前频道 ID 至 `AppPreferences`；应用启动自动恢复上次频道并播放。
  3. **初次启动引导**：首次启动若数据库无频道，自动重置并跳转到源管理界面引导用户添加直播源。

---

## 2. 技术栈与核心依赖

| 层次 | 核心技术 / 库 | 版本 | 职责与说明 |
| :--- | :--- | :--- | :--- |
| **开发语言 & 工具链** | Kotlin + Gradle | Kotlin 2.1+, Gradle 9.1 | JDK 17 (Eclipse Adoptium) |
| **基础组件** | AndroidX Core / AppCompat | 1.18.0 / 1.7.0 | 单 Activity 宿主，基础组件与 AppCompat 支持 |
| **依赖注入** | **Dagger Hilt** | 2.59.2 + KSP | 全局单例、ViewModel 与网络客户端注入 |
| **媒体播放** | **AndroidX Media3 ExoPlayer** | 1.10.0 | HLS / Progressive，结合 OkHttpDataSource 自定义 UA |
| **数据持久化** | **Room Database** | 2.8.4 + KSP | 频道存储，DAO 返回 Flow 驱动 UI 响应 |
| **异步流** | **Coroutines & Flow** | 1.11.0 | 响应式数据流与异步 IO |
| **网络组件** | **OkHttp3** | 4.12.0 | 在线 M3U 下载（宽松证书策略）与流媒体拉流 |
| **局域网配网** | **Java ServerSocket + ZXing**| ZXing 3.5.3 | 内置 Web 服务接收手机配置 |

> [!IMPORTANT]
> **依赖控制原则**：不要随意引入重型第三方库。本项目精简移除了无用的 `androidx.leanback` 与图像加载库，保持极低安装包体积（Release 仅 3.4 MB）和极快编译速度。

---

## 3. 系统架构与模块划分

本项目采用轻量级 **Clean Architecture Lite + MVVM** 单 Activity 架构：

```
UI Layer (Fragments + ViewModel)
    ↕ StateFlow 观察驱动
Domain Layer (Model + Repository 接口 + UseCase)
    ↕ 契约实现
Data Layer (Room + DAO + Preferences + Parser)
```

### 3.1 源码目录结构

```
native-android/app/src/main/java/top/xiaofeigun/tvliveplayer/
├── App.kt                              # Application 入口，标注 @HiltAndroidApp
├── MainActivity.kt                     # 单 Activity 宿主：全屏沉浸、按键总调度、Back 栈控制
├── di/
│   └── AppModule.kt                    # Hilt 依赖注入模块（DB、DAO、Repo、Gson、UnsafeOkHttpClient）
├── player/
│   └── TVPlayer.kt                     # ExoPlayer 封装（状态机、失败重试、自动切备用源）
├── parser/
│   └── M3UParser.kt                    # M3U/TXT 规范解析器（同名频道聚合去重到 backupUrls）
├── util/
│   ├── QrCodeServer.kt                 # 局域网轻量 Web 服务器（接收手机配置与文件上传）
│   └── QrCodeHelper.kt                 # ZXing 二维码 Bitmap 生成
├── domain/
│   ├── model/
│   │   ├── Channel.kt                  # 频道领域实体（含 backupUrls 与 allUrls）
│   │   └── Source.kt                   # 订阅源实体
│   ├── repository/
│   │   └── ChannelRepository.kt        # 数据仓储契约
│   └── usecase/
│       └── ParseAndImportM3UUseCase.kt # 解析并入库 UseCase
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt              # Room 数据库定义 (version = 2)
│   │   ├── dao/ChannelDao.kt           # 频道数据库访问对象
│   │   ├── dao/SourceDao.kt            # 订阅源访问对象
│   │   └── entity/ChannelEntity.kt     # 频道实体持久化模型
│   ├── preferences/
│   │   └── AppPreferences.kt           # SharedPreferences 封装（频道记忆、初次启动标志）
│   └── repository/
│       ├── ChannelRepositoryImpl.kt    # 数据仓储实现
│       └── DefaultChannels.kt          # 默认内置频道列表
└── ui/
    ├── player/
    │   ├── PlayerFragment.kt           # 播放器核心界面（全屏播放、抽屉选台、快捷面板、手势）
    │   └── PlayerViewModel.kt          # UI 状态机（navStack、频道换台、分类切换、收藏）
    └── settings/
        ├── SettingsFragment.kt         # 设置中心页面
        ├── SourceMgmtFragment.kt       # 源管理（扫码导入、URL 解析、清空节目）
        ├── QrInputDialogFragment.kt    # 局域网配网二维码弹窗
        └── UpdateFragment.kt           # 软件更新检查页面
```

### 3.2 核心设计模式

| 模式 | 应用位置 | 职责 |
| :--- | :--- | :--- |
| **MVVM** | `PlayerViewModel` + `PlayerFragment` | 通过单一 `StateFlow<PlayerUiState>` 驱动 UI 渲染 |
| **Repository** | `ChannelRepository` / `ChannelRepositoryImpl` | 隔离数据来源，解耦领域层与数据持久层 |
| **Singleton (DI)** | `@Singleton TVPlayer`, `M3UParser` | 由 Hilt 统一管理生命周期与单例注入 |
| **响应式流** | Room DAO → Repository → StateFlow | 数据库增删改自动推送并更新 UI |
| **责任链调度** | `MainActivity.dispatchKeyEvent` → `PlayerFragment` | 统一按键分发与多级退栈拦截 |

---

## 4. 页面层级与导航状态机 (`navStack`)

页面采用单向栈管理：`PlayerViewModel.state.value.navStack: List<Page>`。

### 4.1 层级架构表

| 层级 | Page 枚举 | 界面展示与形态 | 互斥与回退逻辑 |
| :---: | :---: | :--- | :--- |
| **Level 0** | `PLAYER` | 全屏视频画面 + 左上角 OSD | 栈底页面。按 BACK 提示「再按一次返回键退出」，3 秒内再按退出应用。 |
| **Level 1** | `ICON` | 屏幕右侧快捷面板（设置/源管理/收藏） | **与 CATEGORY 互斥**，通过 `ensureSingleLevel1Path()` 保证同一时间仅存在一个。按 BACK 关闭。 |
| **Level 1** | `CATEGORY` | 底部半透明选台抽屉（分类标签+频道列表） | **与 ICON 互斥**。按 OK 选中换台并回退到 Level 0；按 BACK 关闭。 |
| **Level 2** | `SETTINGS` | 设置页面 (独立 Fragment) | 覆盖于 Level 1 之上。BACK 弹出 Fragment 并退回 Level 1。 |
| **Level 2** | `SOURCE_MGMT` | 源管理页面 (独立 Fragment) | 覆盖于 Level 1 之上。BACK 弹出 Fragment 并退回 Level 1。 |
| **Level 3** | `UPDATE` | 版本检查页面 (独立 Fragment) | 覆盖于 SETTINGS 之上。BACK 退回 SETTINGS。 |

### 4.2 典型导航路径示例
```text
播放器(L0) → 图标面板(L1) → 设置(L2) → BACK → 图标面板(L1) → BACK → 播放器(L0)
播放器(L0) → 分类选台(L1) → OK(选台) → 播放器(L0)
播放器(L0) → 图标面板(L1) → BACK → 播放器(L0)
```

---

## 5. 双模交互与按键映射规范

### 5.1 硬件按键与键盘映射

| 硬件操作 | 映射键值 | 行为说明 |
| :--- | :--- | :--- |
| **电视遥控器** | 方向键 / OK / 菜单 / 返回 | 原生 D-Pad / Center / Menu / Back 事件 |
| **外接键盘** | 方向键 | 等同于遥控器 D-Pad |
| | Enter | 等同于遥控器 OK (KEYCODE_DPAD_CENTER) |
| | Esc | 等同于遥控器 返回 (KEYCODE_BACK) |
| | Menu / App 键 | 等同于遥控器 菜单 (KEYCODE_MENU) |

### 5.2 各层级交互行为矩阵

| 交互动作 | 视频播放中 (Level 0 PLAYER) | 分类选台 (Level 1 CATEGORY) | 图标面板 (Level 1 ICON) |
| :--- | :--- | :--- | :--- |
| **上 / 下** | 切换上一个 / 下一个频道（清除暂停态） | 在频道列表向上 / 向下移动焦点 | 在三个图标按钮间切换焦点 |
| **左 / 右** | 打开分类频道抽屉 | 左右切换分类，刷新频道列表 | 移入按钮 / 移出按钮回到视频区域 |
| **OK / 确定** | 暂停 / 恢复视频播放 | 跳转至焦点频道并播放，关闭抽屉 | 触发聚焦按钮的点击动作 |
| **菜单键** | 打开右侧快捷面板（**不暂停视频**） | 弹出当前频道操作菜单 | 关闭面板 |
| **返回键** | 提示「再按一次返回键退出」 | 关闭抽屉，回到播放 | 关闭面板，回到播放 |
| **触屏单击** | 打开分类频道抽屉 | 单击选中高亮；再次单击同一频道确认换台 | 点击对应按钮触发动作 |
| **触屏双击** | 暂停 / 恢复视频播放 | - | - |
| **触屏上下滑** | 切换上一个 / 下一个频道 | - | - |
| **触屏左右滑** | - | 左右切换分类标签 | - |
| **触屏长按** | 打开右侧快捷面板（不暂停） | 弹出频道操作菜单 | - |

### 5.3 频道操作菜单规范
- **「我的收藏」分类下**：
  - **取消收藏**：从收藏中移除该频道。
- **其他自定义分类下**：
  - **移动到其它分类**：弹出分类列表，将该频道归属更改为目标分类。
  - **删除当前频道**：从数据库中永久移除当前频道。
  - **清空当前分类**：弹出二次确认对话框，确认后删除该分类下全部频道。

---

## 6. 流媒体高可用与容灾保障 (`TVPlayer.kt`)

1. **备用源轮换 (`backupUrls`)**：
   - 播放失败 (`onPlayerError`) 时，若还有备用 URL，展示 `"正在切换源(x/n)..."` 提示，并在 **3 秒延迟** 后尝试下一个地址。
   - 仅当所有备用源全部遍历失败后，才派发 `PlaybackState.ERROR`。
2. **断流自动恢复**：
   - 监听 `Player.STATE_ENDED`：直播流意外结束时，执行 `player.seekTo(0)` + `player.play()` 重启流，状态映射为 `BUFFERING`。
3. **暂停图标控制**：
   - **仅在用户手动按 OK 或触屏双击暂停时**展示暂停图标；断流、缓冲、换源过程中**严禁**显示暂停图标。
4. **SSL 安全策略区分**：
   - **下载在线 M3U 订阅**：使用宽松信任证书客户端 (`@Named("unsafeOkHttpClient")`)，确保支持自签名或证书过期的直播源。
   - **音视频解码播放**：使用系统标准 SSL，确保流媒体拉流规范与性能。

---

## 7. 局域网配网与数据解析

### 7.1 嵌入式 HTTP 服务 (`QrCodeServer.kt`)
- 绑定端口：使用随机可用端口 (`ServerSocket(0)`)。
- 本地 IP：通过枚举网卡获取 IPv4 非回环地址。
- 协议端点：
  - `GET /`：返回响应式 HTML 配网页面（包含添加 M3U 链接、单个频道、上传本地源文件等 Tab）。
  - `POST /submit`：接收 JSON 请求，解析 `m3u_url`、`channel` 或 `file` 内容。

### 7.2 M3U / TXT 双格式解析器 (`M3UParser.kt`)
- 自动识别 `#EXTM3U` 格式或逗号分隔的 TXT 格式（`频道名,URL` 及 `分类名称,#genre#` 分组）。
- **同名频道聚合去重**：按频道名称（忽略大小写和首尾空格）聚合同名频道，提取第一个有效 URL 作为主播放地址，其余地址收纳至 `backupUrls`。

---

## 8. 本地开发、编译与调试指南

### 8.1 本地环境配置
- **本地路径**：
  - JDK 路径：`C:\jdk17\jdk-17.0.19+10`
  - Android SDK 路径：`C:\Android\sdk` (platforms: android-36, build-tools: 35.0.0)
- **PowerShell 环境变量初始化**：
  ```powershell
  $env:JAVA_HOME = "C:\jdk17\jdk-17.0.19+10"
  $env:ANDROID_HOME = "C:\Android\sdk"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  ```

### 8.2 构建与打包命令
- **进入 Android 目录**：`cd native-android`
- **编译 Debug APK**：
  ```powershell
  ./gradlew assembleDebug --no-daemon
  ```
  输出：`native-android/app/build/outputs/apk/debug/app-debug.apk`
- **编译 Release APK（本地免签名构建）**：
  ```powershell
  ./gradlew assembleRelease --no-daemon
  ```
  输出：`native-android/app/build/outputs/apk/release/app-release-unsigned.apk`

### 8.3 模拟器与真机调试
- **MuMu 模拟器 ADB 连接**：
  ```powershell
  adb connect 127.0.0.1:7555
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

### 8.4 CI/CD 自动化构建
- 配置文件：`.github/workflows/build-apk.yml`
- 触发分支：`main`、`master`
- 触发路径：`native-android/**`、`.github/workflows/build-apk.yml`
- 自动化行为：构建 Release/Debug APK，自动创建带有日期版本标签（如 `v2026.10.08`）的 GitHub Release 并挂载 APK 文件。

---

## 9. AI Agent 协作准则与自检清单

当你在后续任务中接手或修改本项目代码时，**必须逐条核对**以下自检项：

1. [ ] **遥控器焦点是否完好？**
   - 任何新增或修改的 View，若需遥控器操作，必须显式设置 `isFocusable = true`。
   - 切换页面后必须显式调用 `requestFocus()`，避免遥控器方向键无响应或焦点跑飞。
2. [ ] **是否遵循 Hilt DI 规范？**
   - 注入的单例与 UseCase 直接使用 `@Inject`，严禁手动 `new`。
   - 需要 Context 时使用 `@ApplicationContext` 或 `@param:ApplicationContext`。
3. [ ] **是否保持单向数据流？**
   - Room 数据变更通过 `Flow` 自动驱动。
   - UI 变化通过 `PlayerViewModel` 的 `_state.update { ... }` 驱动，Fragment 仅负责收集和渲染。
4. [ ] **代码是否有冗余或未使用的资源？**
   - 新增资源必须有明确调用点，废弃代码需彻底清理，不在代码库遗留死代码和无用布局。
5. [ ] **构建验证是否通过？**
   - 修改后必须实际执行 `./gradlew assembleDebug --no-daemon` 验证编译无误，严禁提交无法编译的代码。
6. [ ] **是否获得用户明确的 Git 提交授权？**
   - **严禁私自提交或推送**。未获得用户显式下达的提交指令（例如：“提交代码”、“推送到 GitHub”、“commit and push”）前，保持工作区状态，不得调用 `git commit` 或 `git push`。
