# 万能电视直播 (TV Live Player)

> 面向 Android TV 的纯客户端全屏电视直播应用，基于 React + Vite + Capacitor 构建，遥控器全操控，无需手机端。

---

## 运行环境

| 项目 | 规格 |
|------|------|
| 最低系统 | **Android 6.0** (API 23) |
| WebView 内核 | Chrome 44+ |
| 屏幕方向 | 横屏 |
| 交互方式 | **纯遥控器** (D-pad + OK + Menu + Back) |

---

## 遥控器按键映射

电视遥控器只有 **6 个可用键**：↑ ↓ ← → OK Menu Back

### 各层操作

| 按键 | 纯视频层 | OSD 层 | 分类条 | 频道列表 | 操作区 |
|------|---------|--------|--------|---------|--------|
| **↑ ↓** | 无操作 | 无操作 | ↑回视频 ↓进列表 | 选台 | **退回列表** |
| **← →** | 无操作 | 无操作 | 切分类 | ←分类 →操作区 | ⬆⬇🗑 间切换 |
| **OK** | 弹出 OSD | 打开设置 | 确认分类 | **播放此频道** | 执行操作 |
| **Menu** | 打开设置 | 打开设置 | 打开设置 | 打开设置 | 打开设置 |
| **Back** | 退出提示 | 关闭 OSD | 回视频 | 回视频 | 退回列表 |

### 退出
**纯视频层** 按 Back → 屏幕底部显示 "再按一次返回键退出" → 3 秒内再按 Back → 退出应用。

### 频道操作（频道列表按 → 进入操作区）
- 聚焦操作区后用 ←→ 切换 ⬆上移 / ⬇下移 / 🗑删除，OK 执行

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
│   ├── App.tsx              # 主组件: 四层焦点 + 遥控器导航 + 数据持久化
│   ├── index.css            # 全局样式 (Android 系统字体)
│   ├── types.ts             # TVChannel, CustomSource 类型
│   └── components/
│       ├── TVPlayer.tsx     # 视频播放器 (hls.js)
│       └── CustomSourceModal.tsx  # 设置弹窗 (M3U导入/管理/备份/分类/恢复出厂)
│   └── data/
│       └── defaultChannels.ts  # 初始频道数据 (出厂备份)
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

## GitHub Actions 自动编译

**无需本地编译环境。** Push 到 `main`/`master` 分支自动触发。

### 工作流: `.github/workflows/build-apk.yml`

```
推送代码 → npm install → npm run build → npx cap sync android → ./gradlew assembleDebug → 上传 APK
```

### 下载 APK

1. GitHub 仓库 → **Actions** 选项卡
2. 左侧点击 **Build Android APK**
3. 最新一次成功运行 → 底部 **Artifacts** → 下载 `tv-live-player-apk`

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

任何频道均可删除、排序，均可设置收藏。

---

## 恢复出厂设置

设置弹窗 → 管理 Tab → 底部 🔄 按钮。清除所有 localStorage 数据，恢复 `INITIAL_DEFAULT_CHANNELS`。
