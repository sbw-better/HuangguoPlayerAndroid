# HuangguoPlayer Android 2.0

将 XPTV `huangguo.js` 的核心解析逻辑迁移为原生 Android App。项目仅用于你有权访问的内容，并请遵守目标站点条款及当地法律。

## 2.0 新增

- 原生首页
- 分类浏览：
  - 首页
  - AI成人短剧
  - AI成人漫剧
  - AI换脸
  - AI魔改
  - 排行榜
- 分类分页 / 加载更多
- 搜索历史、点击再次搜索、清空历史
- 收藏
- 最近观看
- 续播位置保存
- 播放完成自动下一集
- 0.75x / 1.0x / 1.25x / 1.5x / 2.0x 倍速
- 横屏沉浸式全屏
- Android 8.0+ 画中画
- 播放失败手动重试
- Media3 / ExoPlayer 原生 HLS 播放
- m3u8 请求自动携带 Referer 与 User-Agent
- 海报 AES-CBC 解密兼容
- 简单应用图标

## 技术栈

- Java
- AndroidX AppCompat 1.8.0
- AndroidX Media3 1.11.0
- HLS / ExoPlayer
- SharedPreferences + JSON 保存收藏、历史、进度
- HttpURLConnection 抓取 HTML / 图片

## 构建环境

项目配置：

- Android Gradle Plugin: `9.4.0`
- Gradle: `9.6.0`（AGP 9.4 官方要求）
- JDK: `17`
- compileSdk: `36`
- targetSdk: `36`
- minSdk: `24`

建议使用当前稳定版 Android Studio（Quail 4 / 2026.1.4 或兼容版本）。

> 当前 ZIP 没有附带 `gradle-wrapper.jar`。Android Studio 打开项目后，如提示配置 Gradle，请选择 Gradle 9.6.0 / JDK 17，或让 Android Studio 创建 Gradle Wrapper。

## 打开项目

1. 解压 ZIP。
2. Android Studio → **Open**。
3. 选择 `HuangguoPlayerAndroid` 文件夹。
4. 等待 Gradle Sync。
5. 如果缺少 Android SDK 36，按 Android Studio 提示安装。
6. Build → Build APK(s)。

Release APK 默认位置：

```text
app/build/outputs/apk/release/app-release.apk
```

## 覆盖安装与签名

Android 仅允许使用**相同签名证书**且 `versionCode` 更高的 APK 覆盖更新。GitHub Actions 已改为构建 release APK，并使用仓库 Secrets 中的固定签名证书；每次工作流运行会自动递增 `versionCode`。

首次配置时，在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 中添加以下 Secrets：

- `ANDROID_KEYSTORE_BASE64`：JKS/keystore 文件的 Base64 内容；
- `ANDROID_KEYSTORE_PASSWORD`：keystore 密码；
- `ANDROID_KEY_ALIAS`：密钥别名；
- `ANDROID_KEY_PASSWORD`：密钥密码。

请长期保存同一份 keystore。更换 keystore 后，已安装的旧版本无法覆盖更新，必须卸载一次；这是 Android 的签名安全机制。

## 播放流程

```text
首页 / 分类 / 搜索
        ↓
/detail/{id}/
        ↓
解析剧集链接
        ↓
播放页 videoInitialData
        ↓
epPlaySrcs[集数] / videoSrc
        ↓
m3u8
        ↓
Media3 HLS 播放
```

## 海报兼容

目标源的部分封面可能是 AES-CBC 加密字节。项目会：

1. 先尝试按站点当前 key/iv 解密；
2. 验证 JPEG / PNG / WEBP / GIF 文件签名；
3. 尝试剥离 PKCS7 padding；
4. 如果判断并非加密图片，则直接使用原始字节。

因此普通图片与加密图片都可以兼容。

## 已知限制

- 目标站点页面结构、分类 URL 或 `videoInitialData` 格式变化后，解析代码可能需要同步更新。
- 某些视频 CDN 可能增加新的 Cookie、Token 或设备校验，届时 Media3 请求头需要继续适配。
- 当前环境没有 Android SDK，因此本项目已做 XML 与 Java 语法级静态检查，但尚未在此环境完成真实 Android 编译。
- 全屏目前采用横屏 + 沉浸式系统栏隐藏；不同 ROM 的系统导航行为可能略有差异。

## 下一步建议

如果继续开发，优先级建议：

1. 真机编译和播放测试，修复站点兼容问题；
2. 拆分 `MainActivity`，增加 Repository / Parser / PlayerManager，降低维护成本；
3. RecyclerView + ViewModel，改善大量封面时的性能；
4. Media3 Cast，增加电视投屏；
5. 本地缓存 / 离线缓存（仅限有权缓存的内容）；
6. Release 签名与正式 APK/AAB 打包。

## GitHub Actions 自动生成 APK

项目已经包含：

```text
.github/workflows/android-build.yml
```

因此不依赖本地 Gradle Wrapper 也能在 GitHub 上构建：

1. 把整个项目上传到 GitHub 仓库；
2. 打开仓库 **Actions**；
3. 选择 **Android APK Build**；
4. 点击 **Run workflow**；
5. 构建成功后，在该次运行页面底部下载：


```text
HuangguoPlayer-release-apk
```

其中包含：

```text
app-release.apk
app-release.apk.sha256
```

Workflow 使用 JDK 17、Android SDK 36、Build Tools 36.0.0、Gradle 9.6.0，并执行 `:app:assembleRelease`。

## 自动发布与应用内更新

每次向 `main` 分支推送代码后，GitHub Actions 会自动：

1. 构建并签名 release APK；
2. 上传临时构建产物；
3. 创建一个 GitHub Release，并附上 APK、SHA256 文件与 `update.json` 更新元数据；
4. 若配置了 Gitee 发布令牌，也会同步创建 Gitee Release。

应用每次冷启动时都会检查 Gitee 的最新 Release。新版本优先读取结构化的 `update.json`，旧版本仍兼容 Release 文案中的 `versionCode`；下载完成后会校验 APK 的 SHA-256，再打开 Android 系统安装确认页。下载任务会被保存，应用在下载期间被关闭后重新打开也能继续处理。

### 配置 Gitee 更新源

更新仓库固定为 `https://gitee.com/sibingwei/huangguoplayer-update`。在 Gitee 的个人访问令牌页面创建一个拥有仓库发布权限的令牌（建议只授予 `projects` 权限），然后在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 中添加 Secret：

```text
GITEE_TOKEN
```

令牌只保存在 GitHub Secrets，绝不能写入 APK、代码或 Issue。首次配置后，下一次 Actions 成功构建会自动初始化 Gitee 仓库并创建 Release。当前已发布的旧版 App 仍访问 GitHub；需手动安装一次本次新 APK 后，后续应用内更新才会通过 Gitee 下载。

Android 不允许普通应用静默安装更新。首次更新时，系统会要求授权“允许此应用安装未知应用”，之后仍需要在系统安装页确认。
