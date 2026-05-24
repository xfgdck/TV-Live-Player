# 万能电视直播 (TV Live Player)

> 面向 Android TV 的纯客户端全屏电视直播应用，基于 React + Vite + Capacitor 构建，遥控器全操控，无需手机端。

---

<!-- AI_SESSION_START -->
## 🤖 AI 会话快速索引

> **新 AI 会话必读** — 按顺序理解项目：

| # | 要了解的内容 | 关键文件 | 说明 |
|---|------------|---------|------|
| 1 | **项目是什么** | 本 README | 电视直播 App，Android TV 遥控器操控 |
| 2 | **类型定义** | `src/types.ts` | `TVChannel`, `CustomSource` 数据结构 |
| 3 | **主组件 (核心逻辑)** | `src/App.tsx` | 状态管理、导航系统、数据持久化、遥控器按键处理 |
| 4 | **视频播放器** | `src/components/TVPlayer.tsx` | hls.js 解码、全屏支持、加载/错误状态 |
| 5 | **设置弹窗** | `src/components/CustomSourceModal.tsx` | 5个Tab：M3U订阅、手动添加、频道管理、订阅源与分类、备份恢复 |
| 6 | **初始数据** | `src/data/defaultChannels.ts` | 出厂频道数据 + 预设 M3U 源 |
| 7 | **M3U 解析器** | `src/utils/m3uParser.ts` | 客户端解析 M3U/M3U8 格式 |
| 8 | **样式** | `src/index.css` | Tailwind CSS v4 + Android 系统字体 |
| 9 | **构建配置** | `vite.config.ts` | Vite + 兼容性插件（Android 6 降级） |
| 10 | **CI/CD** | `.github/workflows/build-apk.yml` | GitHub Actions 自动编译 APK |

### 核心架构速览

```
App.tsx (主控)
  ├── TVPlayer.tsx        ← 视频播放层 (Layer 0)
  ├── OSD Info 层         ← OK键显示 3秒 (Layer 1)
  ├── Category Picker     ← ← → 触发 (Layer 2)
  └── CustomSourceModal   ← Menu键触发 (Layer 99)
```

### 导航状态机 (v2.0 简化版)

```
          watching ←─────────────────────┐
         /    |    \                      │
     ↑↓换台  ←→切分类  OK=信息           │ 超时4s / Back
                  |                      │
                  ↓                      │
             categories ─────────────────┘
           (分类选择器浮层)
         ← → 选分类 · OK 确认
```

### 数据流

```
localStorage ←→ App.tsx (state) → TVPlayer (播放)
                    ↕
           CustomSourceModal (编辑)
```

### ⚠️ AI 工作规范（每次会话必须遵守）

1. **修改源码后自动更新本 README** — 如果改动涉及导航逻辑、按键映射、组件结构、数据流、Tab 变化等，必须同步更新 README 中对应的文档段落。
2. **优先读取 README 而非全部源码** — 本 README 已包含完整的架构说明和导航状态机，先用 `Read` 读 README 了解全貌，再按需读具体文件。
3. **用 Grep 代替全文件 Read** — 需要定位代码时，优先 `Grep` 搜索关键字，避免通读整个文件。
4. **并行读取无关文件** — 同时读取多个独立文件时，在一次调用中并行发起。
<!-- AI_SESSION_END -->

---

## 运行环境

| 项目 | 规格 |
|------|------|
| 最低系统 | **Android 6.0** (API 23) |
| WebView 内核 | Chrome 44+ |
| 屏幕方向 | 横屏 |
| 交互方式 | **纯遥控器** (D-pad + OK + Menu + Back) |
| 默认播放 | **全屏播放**（切换频道自动全屏，双击视频切换） |

---

## 遥控器按键映射 (v2.0)

电视遥控器只有 **6 个可用键**：↑ ↓ ← → OK Menu Back

### 观看模式（默认）

| 按键 | 行为 | 说明 |
|------|------|------|
| **↑ ↓** | 切换频道 | 在当前分类内循环切换上/下一个频道 |
| **← →** | 弹出分类选择器 | 显示分类浮层，默认高亮相邻分类 |
| **OK** | 显示 OSD 信息 | 显示频道名称、分类、操作提示，3秒自动消失 |
| **Menu** | 打开设置中心 | 进入编辑模式（详见下方） |
| **Back** | 退出应用 | 按一次提示，3秒内再按确认退出 |

### 分类选择器（← → 触发，4秒无操作自动消失）

| 按键 | 行为 |
|------|------|
| **← →** | 在分类标签间切换 |
| **OK** | 确认选择 → 切换到该分类第一个频道 |
| **↑ ↓** | 取消选择器，同时切换频道 |
| **Back** | 取消选择器，回到观看 |
| **Menu** | 打开设置中心 |

### 设置中心（Menu 键唤出，Back 键关闭）

设置中心共 **5 个 Tab**，所有编辑操作仅限于此页面：

| Tab | 功能 |
|-----|------|
| **订阅 M3U** | 一键导入预设源 / 在线链接 / 本地文件 / 粘贴文本 |
| **手动添加** | 表单添加单个频道（名称/URL/分类/Logo） |
| **频道管理** | 搜索/重命名/排序(上移下移)/删除频道 |
| **订阅源与分类** | 管理订阅源 + 分类重命名/删除 + 恢复出厂设置 |
| **备份恢复** | 导出/导入 JSON 备份文件 |

---

## 架构概览

```
d:\TV-Live-Player\
├── index.html               # Vite 入口 (dev)
├── vite.config.ts           # 构建配置 + 旧版兼容
├── package.json
├── tsconfig.json
├── capacitor.config.json    # Capacitor 配置
├── .github/workflows/
│   └── build-apk.yml        # GitHub Actions 自动编译
├── src/
│   ├── main.tsx             # React 入口
│   ├── App.tsx              # 主组件: 两层焦点状态机 + 遥控器导航 + 数据持久化
│   ├── index.css            # 全局样式 (Android 系统字体)
│   ├── types.ts             # TVChannel, CustomSource 类型
│   ├── components/
│   │   ├── TVPlayer.tsx     # 视频播放器 (hls.js) + 全屏支持
│   │   ├── TVRemoteWidget.tsx  # 虚拟遥控器 UI (未集成，预留)
│   │   └── CustomSourceModal.tsx  # 设置中心 (5 Tab: M3U/手动/频道管理/源与分类/备份)
│   ├── data/
│   │   └── defaultChannels.ts  # 初始频道数据 (出厂备份) + 预设 M3U 源
│   └── utils/
│       └── m3uParser.ts     # M3U 文件解析器
├── dist/                    # 构建产物 (Capacitor 从这里同步)
└── android/                 # Capacitor Android 原生壳 (GitHub Actions 运行时生成)
```

### 技术栈

| 层 | 技术 |
|----|------|
| UI 框架 | React 19 + TypeScript |
| 样式 | Tailwind CSS v4 |
| 构建 | Vite 6 |
| 打包 | Capacitor 6 → APK |
| 视频解码 | hls.js |
| CI/CD | GitHub Actions |
| 图标 | lucide-react |

---

## GitHub Actions 自动编译 & 发布

**无需本地编译环境。** Push 到 `main`/`master` 分支自动编译并发布到 Release。

### 工作流: `.github/workflows/build-apk.yml`

```
推送代码 → npm install → npm run build → npx cap sync android → ./gradlew assembleDebug → 发布 Release
```

### 📥 下载 APK（推荐：从 Release 下载）

1. GitHub 仓库 → 右侧 **Releases** 栏
2. 点击最新 Release（`release-{编号}`）
3. 下载 `tv-live-player-debug.apk`

> 每次成功推送后自动创建 Release，无需等待 Actions artifact 过期（14天）。

### 备选：从 Actions Artifact 下载

GitHub 仓库 → **Actions** → 最新运行 → 底部 Artifacts → 下载 `tv-live-player-apk`

### 关键配置
- Node.js 20
- Java JDK 17 (Zulu)
- 编译目标: `targetSdkVersion = 23`, `minSdkVersion = 22`

---

## CSS / JS 兼容性处理 (Android 6 关键)

Android 6 WebView 内核为 Chrome 44-51，不支持以下现代特性，构建时均已自动降级：

| 不兼容特性 | Chrome 版本要求 | 处理方式 |
|------------|:--:|------|
| `<script type="module">` | 61+ | `@vitejs/plugin-legacy` → SystemJS 降级 |
| `@layer` | 99+ | 自定义 Vite 插件剥离 `@layer` 包装块 |
| `oklch()` / `lab()` | 111+ | lightningcss 转为 `rgb()` / hex |
| `:where()` / `:is()` | 88+ | 自定义 Vite 插件展开为平级选择器 |
| `@property` | 85+ | 构建后移除 |
| `color-mix()` | 111+ | 替换为直接颜色值 |
| `Proxy` | 49+ | `proxy-polyfill` (Google 出品, 2.5KB) |
| ES6+ 语法 | — | `@babel/preset-env` 转 ES5 |
| Google Fonts 外链 | — | 改为 Android 系统字体 (Roboto / Noto Sans SC) |
| `https` scheme | — | Capacitor 改为 `http` + `allowMixedContent` |

构建后 `dist/index.html` 由 `htmlCompatPlugin` 完全重写为纯 `<script>` 串行加载，不依赖 `type="module"` / `nomodule` / `crossorigin`。

---

## 数据持久化

所有数据存储在 `localStorage`，切换频道时**立即写入**，崩溃不丢失。

| Key | 类型 | 说明 |
|-----|------|------|
| `tv_channels` | `TVChannel[]` | 全部频道 (初始 = DEFAULT_CHANNELS) |
| `tv_favorites` | `string[]` | 收藏的频道 ID |
| `tv_sources` | `CustomSource[]` | 已订阅的 M3U 播放源 |
| `tv_last_channel` | `string` | 上次退出的频道 ID (启动恢复) |

---

## 频道模型

**所有频道平等** — 无"内置/自定义"区分。`INITIAL_DEFAULT_CHANNELS` 仅用于：
1. 首次启动填充
2. "恢复出厂设置"还原

任何频道均可删除、排序、重命名，均可设置收藏。

---

## 恢复出厂设置

设置中心 → 订阅源与分类 Tab → 底部 🔄 按钮。清除所有 localStorage 数据，恢复 `INITIAL_DEFAULT_CHANNELS`。
