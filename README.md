# TV Live Player (网络电视直播播放器)

一款面向大屏 TV、移动端及网页端的跨平台**纯客户端网络电视播放器**。支持一键快速导入 `.m3u` / `.m3u8` 直播订阅源、触屏手势、键盘及遥控器上下换台切换，100% 保证用户隐私，完全不需要搭建任何中转云端服务器。

---

## ✨ 核心特性

- **100% 离线与隐私安全**：所有的订阅源解析、电视频道存储、状态记忆及直播流播放完全在您的本地浏览器沙箱或安卓 APP 内部进行，无任何后台数据上传。
- **全方位导入机制**：
  - **直接拖拽或手动选择本地 M3U / M3U8 文件**完美解析（100% 免除一切 CORS 跨域网页拦截）。
  - **直接粘贴 M3U 文本内容**解析。
  - **输入公网 M3U 订阅源链接**直接请求（自带公共 CORS 跨域路由 fallback）。
- **流畅解码**：内嵌成熟稳定的 `hls.js` 引擎，可流畅解码并加载各大公开精修的高清播放源视频。
- **跨平台操控适配**：支持键盘 <kbd>↑</kbd> / <kbd>↓</kbd> 键极速秒换台，完美适配传统智能电视遥控器或安卓盒子。

---

## 🚀 编译与打包方案

本项目已经移除了所有第三方后端服务器及 Gemini AI 服务依赖，完全重构为**轻量级 SPA（单页面应用）前端应用**，并采用现代 **Capacitor** 容器将其桥接编译为原生 Android 安装包（.apk）。

您拥有以下两种主要方案来生成专属的 `.apk` 安装包：

### 方案一：使用 GitHub Actions 配置云端自动化编译（省时、省力外置编译，推荐 🌟）

无需在本地配置任何复杂的 Android 软件开发环境（Java、Android SDK 等），直接将此代码仓库托管至 GitHub，即可自动云端构建：

1. **推送代码**：将代码提交并 `git push` 到您的 GitHub 仓库的 `master` 或 `main` 分支。
2. **触发构建**：项目根目录已置入 `.github/workflows/build-apk.yml` 自动化文件。每次您推送代码时，GitHub Actions 就会自动启动打包。
3. **获取安装包 (.apk)**：
   - 打开您的 GitHub 仓库，点击顶部的 **Actions** 选项卡。
   - 在左侧列表中，点击 **Build Android APK** 工作流。
   - 点击最新一次运行成功的记录，拉到页面最下方的 **Artifacts** 区域。
   - 看到名为 `tv-live-player-apk` 的构建产物，点击即可下载已打包完毕的安卓 `.apk` 文件。

---

### 方案二：在 Windows 11 本地电脑进行编译

如果您想在自己的本地 Windows 11 电脑完成 Android APK 构建，请按照以下完整的操作步骤配置并执行。

#### 📌 前期基础工具准备

在开始编译前，请确保您的 Windows 11 系统中安装了以下核心工具：

1. **Node.js**: [下载并安装 Node.js 18 或 20 LTS](https://nodejs.org/)。
2. **Java JDK 17**: [下载并安装 Java JDK 17](https://www.oracle.com/java/technologies/downloads/#java17)（推荐使用 Azul Zoo 或 Oracle JDK，安装完毕后请确保系统环境变量 `JAVA_HOME` 指向 JDK 的根目录）。
3. **Android Studio**: [下载并安装 Android Studio](https://developer.android.com/studio)。
   - 打开 Android Studio 偏好设置中的 **SDK Manager**，下载并安装 **Android SDK (建议 API 33 及以上)**。
   - 完成安装后，请在系统的环境变量中配置 `ANDROID_HOME`。例如指向：`C:\Users\您的用户名\AppData\Local\Android\Sdk`。

#### 🛠️ Windows 11 本地具体编译步骤

打开 Windows 终端（PowerShell 或 CMD），进入该项目代码的根目录，按顺序执行以下命令：

##### 第一步：安装前端依赖库
```bash
npm install
```

##### 第二步：打包前端静态资源 (SPA)
```bash
npm run build
```
执行完毕后，项目根目录下会生成一个 `dist` 静态资源目录。

##### 第三步：添加并初始化 Capacitor 安卓原生层
*（如果您的项目根目录下尚未生成 `android` 目录，请运行本条添加命令）*
```bash
npx cap add android
```

##### 第四步：同步分发前端代码到安卓平台工程
每当您修改了前端代码（运行了 `npm run build`）之后，都需要运行同步指令将最新的网页更新传到安卓原生包裹工程中：
```bash
npx cap sync android
```

##### 第五步：编译打包 APK
###### 方法 A（推荐：通过命令行快速直接打包生成 APK）
进入 `android` 目录，直接借助生成的 Gradle 命令在本地环境快速自动构建：
```powershell
cd android
# 在 Windows Powershell 下执行本地构建（请确保配置好 $env:JAVA_HOME）
.\gradlew.bat assembleDebug
```
**🎉 APK 产物路径**：
编译成功后，生成的通用测试版 APK 文件将在以下路径：
`安卓文件夹根目录\android\app\build\outputs\apk\debug\app-debug.apk`
您可以直接通过微信、数据线等将该 `.apk` 文件发送并安装至您的安卓智能电视机、安卓手机或电视盒子上。

###### 方法 B（通过 Android Studio 可视化管理与调试）
如果您需要更换 APP 图标、包名，或希望以签名发布（Release）的形式生成安全的线上商用包：
```bash
# 这将会为您自动在 Android Studio 中拉起我们的安卓项目工程
npx cap open android
```
- Android Studio 完整载入项目后，可以在顶部菜单栏选择 `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)` 进行一键本地生成。
- 若要真机或模拟器连调，可用 USB 数据线连接安卓测试设备，在开发者模式下点击编辑器的绿色 **Run (Run 'app')** 箭头按钮在线调试。

---

## 🛠️ 主流电视直播源参考资源

您可以利用自带的播放器配置模块方便地加入国内外著名的精修 M3U 公开直连线路（如范明明的精修 IPv6 经典源、以及其他肥样精修源等）。由于全客户端运行在您的本地网络，如果您是在国内运行，请确保您的宽带支持并开启了 IPv6（如光猫设置），以便流畅秒播极速高清频道。
