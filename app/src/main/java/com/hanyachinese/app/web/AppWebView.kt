package com.hanyachinese.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Hanya Chinese 统一 WebView 封装
 *
 * 核心职责：
 * 1. 开启 JS + 媒体自动播放（mediaPlaybackRequiresUserGesture=false, 允许 TTS 发音/听力自动播放）
 * 2. 麦克风权限（onPermissionRequest 是 WebView getUserMedia 的总开关, 跟读要用）
 * 3. 音频焦点管理（TTS 朗读与来电/系统音冲突处理）
 * 4. Cookie 持久化（登录态保存）
 * 5. 深链/站内导航处理（保持应用内, 不跳系统浏览器）
 *
 * 注意: 必须提供标准 WebView 构造(Context/AttributeSet), 供 XML inflate;
 *       URL 加载由外部调用 loadUrl().
 */
@SuppressLint("SetJavaScriptEnabled")
class AppWebView : WebView {

    /** 页面加载进度回调(供 MainActivity 显示进度条) */
    var onProgressChanged: ((Int) -> Unit)? = null

    // 标准 WebView 构造(供 XML inflate 与代码 new)
    constructor(context: Context) : super(context) { setup() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { setup() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { setup() }

    init { configureCookieSync() }

    private fun setup() {
        if (isInEditMode) return // 编辑器预览跳过设置
        configureWebSettings()
        configureChromeClient()
        configureWebViewClient()
    }

    private fun configureWebSettings() {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.mediaPlaybackRequiresUserGesture = false // 关键: 允许 JS 自动播放 TTS/听力
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.databaseEnabled = true
        settings.setSupportZoom(false)
        settings.blockNetworkLoads = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.textZoom = 100
    }

    private fun configureChromeClient() {
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged?.invoke(newProgress)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val resources = request.resources
                // 授予音频捕获(麦克风, 用于跟读发音). 只对 hanyabuy 域授予, 其他拒绝
                if (resources.isNotEmpty() &&
                    request.origin.host?.contains("hanyabuy", ignoreCase = true) == true
                ) {
                    request.grant(resources)
                } else {
                    request.deny()
                }
            }
        }
    }

    private fun configureWebViewClient() {
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // lang 站内(https域名)保持应用内
                if (url.startsWith("https://lang.hanyabuy.com")) {
                    view?.loadUrl(url)
                    return true
                }
                // 其他 https 目标(如 kf.hanyabuy.com 客服)也应用内, 保证体验
                if (url.startsWith("https://")) {
                    view?.loadUrl(url)
                    return true
                }
                // 非 http 方案(scheme:// 等)交给系统
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // 弱网/404 错误交给前端处理(前端有双语错误提示), 这里不弹系统框
                super.onReceivedError(view, request, error)
            }
        }
    }

    /** Cookie 持久化: 登录态保存, 重开 App 仍登录 */
    private fun configureCookieSync() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }

    /** 获得音频焦点(TTS 朗读前) */
    fun requestAudioFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
    }

    /** 释放音频焦点 */
    fun abandonAudioFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.abandonAudioFocus(null)
    }
}
