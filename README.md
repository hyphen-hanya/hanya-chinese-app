# Hanya Chinese App (韩亚中文 · 语言学习)

> hanyabuy 语言学习 App - 安卓版
> 加载 https://lang.hanyabuy.com (Hanya Chinese 在线语言学习平台)

## 技术方案
- **纯原生 Kotlin WebView 套壳**（砍 Capacitor，稳）
- **AGP 8.4.2 + Kotlin 1.9.24 + Gradle 8.7 + JDK 17**（官方互认配对，见蓝本踩坑#1）
- compileSdk 34 / minSdk 24 / targetSdk 34
- **单 WebView 全屏**：lang 站自带底部导航(Home/Learn/Teach/Me)，App 不重复套壳
- 原生增强：麦克风权限(跟读发音)、TTS 自动播放、返回键后退、下拉刷新、加载进度条、深链(lang.hanyabuy.com/xxx)

## 结构
```
hanya_chinese_app/
├── build.gradle / settings.gradle / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties   # pin gradle-8.7
├── .github/workflows/build-apk.yml            # CI 云端构建(本机无工具链)
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/hanyachinese/app/
        │   ├── ui/MainActivity.kt       # 单WebView + 麦克风 + 返回键 + 深链
        │   └── web/AppWebView.kt        # WebView封装: 权限/TTS/音频焦点/Cookie
        └── res/                          # layout/values/drawable图标
```

## 凭证
- GitHub: `hyphen-hanya` (repo+workflow)，仓库 `hyphen-hanya/hanya-chinese-app`
- keystore: CI gen-keystore 生成 `hanya-release.keystore`，密码 `HanyaChinese2026!Secure`，alias `hanyachinese`
- 详凭据 → `.vault/credentials/github/`（待建）

## 构建/发布
- 推 main → CI 自动构建 debug + release APK
- keystore 从 GitHub Secrets: HANYACHINESE_KEYSTORE_BASE64 / _PASSWORD / KEY_ALIAS / KEY_PASSWORD
- 发布: GitHub Releases 直装链接 + 三星 Galaxy Store

## 参考蓝本
- `services/hanyabuy_app/`（hanyabuy 全家桶 App，含全部踩坑）
- 契库 `PROJECT_HanyabuyApp_APP制作全记录_20260816.md`

## 部署铁律
- 本机无 Java/Gradle/SDK → **纯云端 CI 构建**，本机只编辑+推送+轮询
- 改代码让参谋(FD/FG)写，自己只复核+部署+备份
- keystore 是命根：及时下载+本地/外挂双备份，丢了无法更新
