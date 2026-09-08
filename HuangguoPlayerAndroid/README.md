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

Debug APK 默认位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

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
HuangguoPlayer-debug-apk
```

其中包含：

```text
app-debug.apk
app-debug.apk.sha256
```

Workflow 使用 JDK 17、Android SDK 36、Build Tools 36.0.0、Gradle 9.6.0，并执行 `:app:assembleDebug`。
