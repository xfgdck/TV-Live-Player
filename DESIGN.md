# TV-Live-Player

Android TV 直播播放器

## 一、设计总则

### 支持的交互方式

| 方式 | 按键 | 功能映射 |
|------|------|----------|
| 遥控器 | 上/下/左/右/OK/返回/菜单 | 原生按键 |
| 键盘 | 上/下/左/右 | 方向键 |
| | Enter | 等同于遥控器 OK |
| | Esc | 等同于遥控器 返回 |
| | 菜单键 / 应用键 | 等同于遥控器 菜单 |
| 触摸屏（PLAYER 页面） | 单击 | 打开分类导航 |
| | 双击 | 暂停/恢复播放 |
| | 长按 | 打开图标面板（焦点在设置按钮） |
| | 上下滑动 | 切换频道 |
| 触摸屏（CATEGORY 页面） | 左右滑动 | 切换分类 |
| | 单击频道 | 聚焦选中，再次单击同一个频道确认跳转 |
| | 长按频道 | 弹出频道操作菜单 |

### 全局约定

- **音量**：由系统完全控制，软件内部无音量调节
- **频道记忆**：每次换台立即保存当前频道 ID；启动自动恢复上次频道并播放
- **安装包**：通用 APK，兼容 32/64 位，Android 6 ~ 16

## 二、页面层级与导航

### navStack 路径系统

页面按层级组织为一条单向路径，BACK 严格逐层返回，不能跨级跳转。

| 层级 | 页面 | 说明 |
|------|------|------|
| 0 | PLAYER | 全屏播放 |
| 1 | ICON / CATEGORY | 图标面板 / 分类选台（**互斥**，同一时间只存在一个） |
| 2 | SETTINGS / SOURCE_MGMT | 设置 / 源管理（独立 Fragment，navStack + FragmentManager 同步管理） |
| 3 | UPDATE | 软件更新（独立 Fragment，未加入 navStack，BACK 回退到 SETTINGS） |

### 导航路径示例

```
播放器 → 图标面板 → 设置 → BACK → 图标面板 → BACK → 播放器
播放器 → 分类选台 → OK(选台) → 播放器
播放器 → 图标面板 → BACK → 播放器
```

### BACK 键行为

- **Level 0（PLAYER，无覆盖层）**：显示「再按一次返回键退出」，3 秒内再按退出；其他按键或超时取消
- **Level 1（ICON / CATEGORY）**：关闭覆盖层，回到 Level 0
- **Level 2+（SETTINGS / SOURCE_MGMT）**：退回到上一层（Level 1），FragmentManager 同步弹出

## 三、Level 0 – 全屏播放界面

### 界面

- 全屏视频画面
- 左上角浮动显示当前频道名称（OSD，3 秒自动隐藏）
- 暂停时画面中央显示暂停图标，仅由用户主动暂停时出现

### 按键行为

| 按键 | 视频播放中 | 视频已暂停 |
|------|-----------|-----------|
| **上 / 下** | 切换上/下一个频道（清除暂停状态） | ← 同左 |
| **左 / 右** | 打开分类频道选择 | ← 同左 |
| **OK** | **暂停**视频 | **恢复**播放 |
| **菜单** | 打开图标面板（**不暂停**，焦点在「设置」按钮） | 打开图标面板（焦点在「设置」按钮） |
| **返回** | 退出确认提示 | ← 同左 |

## 四、Level 1 – 图标面板 (ICON)

### 进入方式

- 播放中按 **菜单**：不暂停 + 打开面板，焦点在「设置」按钮
- 触摸 **长按**：不暂停 + 打开面板，焦点在「设置」按钮

### 界面

屏幕右侧三个竖排按钮：

```
┌──────────┐
│ ⚙ 设置   │
├──────────┤
│ 📡 源管理 │
├──────────┤
│ ☆ 收藏   │
└──────────┘
```

- 收藏按钮显示实时状态（空心☆ / 实心★）

### 按键行为

| 按键 | 焦点在视频 | 焦点在按钮 |
|------|-----------|-----------|
| **右键** | 焦点移到第一个按钮（设置） | 无反应 |
| **左键** | 无反应 | 焦点回到视频区域 |
| **上 / 下** | 无反应 | 在按钮间移动焦点 |
| **OK** | 恢复播放 + 关闭面板 | 触发按钮动作 |
| **菜单** | 关闭面板 | ← 同左 |
| **返回** | 关闭面板 | ← 同左 |

### 按钮动作

| 按钮 | 动作 |
|------|------|
| 设置 | 推入 SETTINGS 页面（Level 2），覆盖当前界面 |
| 源管理 | 推入 SOURCE_MGMT 页面（Level 2），覆盖当前界面 |
| 收藏 | 切换当前频道收藏状态，弹出 Toast 提示，面板保持打开 |

## 五、Level 1 – 分类频道选择 (CATEGORY)

### 进入方式

- Level 0 按 **左 / 右** 进入
- 触摸单进入（PLAYER 页面单击）

### 界面

- 底部半透明覆盖层
- 上方：当前分类的频道列表（竖向排列）
- 下方：分类标签栏（横向排列，可左右滚动）
- 系统内置「我的收藏」分类

### 按键行为

| 按键 | 行为 |
|------|------|
| **左 / 右** | 切换分类，频道列表刷新，焦点停在第一个频道 |
| **上 / 下** | 移动频道焦点 |
| **OK** | 跳转至焦点频道并播放，关闭覆盖层，回到 Level 0 |
| **菜单** | 弹出频道操作菜单（我的收藏：取消收藏 / 其他分类：移动到其它分类、删除当前频道、清空当前分类） |
| **返回** | 取消选择，关闭覆盖层，回到 Level 0 |

### 触摸行为

| 手势 | 行为 |
|------|------|
| 单击频道 | 聚焦选中该频道（不跳转） |
| 再次单击同一频道 | 跳转至该频道并播放，关闭覆盖层 |
| 长按频道 | 弹出频道操作菜单 |
| 左右滑动 | 切换分类 |

### 频道操作菜单

**我的收藏分类：**
- **取消收藏**

**其他分类：**
- **移动到其它分类**：将频道移动到选中的目标分类
- **删除当前频道**：从数据库移除该频道
- **清空当前分类**：二次确认后删除该分类下所有频道

## 六、Level 2 – 设置界面 (SETTINGS)

### 功能

- 软件更新检查（显示版本号，检查更新按钮仅弹出 Toast 提示）

## 七、Level 2 – 源管理界面 (SOURCE_MGMT)

### 功能

| 功能 | 说明 |
|------|------|
| 扫码添加（QR） | 嵌入式 HTTP 服务器 + ZXing 二维码，手机端扫码后提交 M3U 链接 / 单个频道 / 文件上传 |
| 从 URL 导入 | 输入 M3U 链接，在线下载并解析导入（使用**非严格 SSL** OkHttpClient） |
| 清空节目 | 删除所有频道 |

## 八、流可靠性保证

| 场景 | 处理方式 |
|------|----------|
| 直播流断开（STATE_ENDED） | 自动 seek(0) + play 重启，状态映射为 BUFFERING |
| 播放错误（onPlayerError） | 3 秒延迟后尝试下一备用 URL，遍历所有 URL 后停止 |
| 暂停图标 | **仅**在用户手动按 OK 暂停时显示；断流/错误**不会**显示暂停图标 |

## 九、技术说明

### 关键依赖

| 依赖 | 版本 |
|------|------|
| ExoPlayer (Media3) | 1.5.1 |
| Hilt | 2.53.1 |
| Room | 2.6.1 |
| Coil | 3.0.4 |
| OkHttp | 4.12.0 |

### SSL 说明

- M3U 下载：使用 `buildUnsafeOkHttpClient()`（信任所有证书）
- 视频播放：使用系统默认 SSL

### 构建环境

| 工具 | 路径 / 版本 |
|------|-------------|
| JDK | C:\jdk17\jdk-17.0.19+10 |
| SDK | C:\Android\sdk (platform android-36, build-tools 35.0.0) |
| Gradle | 8.9 / AGP 8.7.3 / Kotlin 2.1.0 |
| 模拟器 | MuMu (ADB 127.0.0.1:7555) |

### 包名

| 项目 | 值 |
|------|-----|
| namespace / applicationId | `top.xiaofeigun.tvliveplayer` |
| 应用签名 | release.jks（GitHub Secrets 管理） |

## 十、项目架构

### 整体架构

采用 **Clean Architecture lite** 三层架构：

```
UI Layer (Fragments + ViewModel)
    ↕ StateFlow 观察
Domain Layer (Model + Repository 接口)
    ↕ 接口实现
Data Layer (Room + Preferences)
```

### 包结构

```
top.xiaofeigun.tvliveplayer/
├── App.kt                    # @HiltAndroidApp 入口
├── MainActivity.kt           # 单 Activity 宿主
├── di/AppModule.kt           # Hilt DI 模块
├── domain/
│   ├── model/                # Channel, Source 纯 Kotlin 数据类
│   ├── repository/           # ChannelRepository 接口
│   └── usecase/              # ParseAndImportM3UUseCase
├── data/
│   ├── local/                # Room 数据库 + Entity + DAO
│   ├── preferences/          # SharedPreferences 封装
│   └── repository/           # ChannelRepositoryImpl 实现
├── parser/M3UParser.kt       # M3U / TXT 双格式解析器
├── player/TVPlayer.kt        # ExoPlayer 封装（URL 自动换源）
├── util/
│   ├── QrCodeHelper.kt       # ZXing 二维码生成
│   └── QrCodeServer.kt       # 嵌入式 HTTP 服务器
└── ui/
    ├── player/               # PlayerFragment + PlayerViewModel
    ├── channel/              # ChannelEditFragment
    ├── settings/             # 设置、源管理、QR 扫码等
    └── menu/                 # MainMenuDialogFragment
```

### 数据流

```
M3U URL → OkHttp → M3UParser → ChannelRepository → Room DB
                                                      ↓ (Flow 观察)
                                              PlayerViewModel (StateFlow)
                                                      ↓
                                              PlayerFragment 渲染 UI
```

- Room DAO 返回 `Flow<List<T>>`，数据变更自动推送
- ViewModel 通过 `StateFlow<PlayerUiState>` 驱动 UI
- TVPlayer 独立维护 `playbackState` StateFlow，Fragment 直接收集

### 导航系统

双重导航：

| 机制 | 用途 |
|------|------|
| `navStack` (Page 枚举列表) | 覆盖层页面：PLAYER / ICON / CATEGORY / SETTINGS / SOURCE_MGMT / UPDATE |
| FragmentManager | 全屏 Fragment 切换：Settings / SourceMgmt / Update |

- `ensureSingleLevel1Path()` 保证 ICON 和 CATEGORY 互斥
- BACK 键按 navStack 逐层返回，Level 0 显示退出确认

### 关键设计模式

| 模式 | 应用 |
|------|------|
| MVVM | StateFlow + Fragment 观察 |
| Repository | 接口/实现分离 |
| Singleton (Hilt) | TVPlayer、M3UParser、AppPreferences |
| 观察者 (Flow) | Room → ViewModel → UI |
| 职责链 | Activity 按 navStack 分发按键事件 |
| URL 换源 | Player 失败自动切换到 backupUrls |
| 嵌入 HTTP 服务 | QrCodeServer 单线程 ServerSocket |
