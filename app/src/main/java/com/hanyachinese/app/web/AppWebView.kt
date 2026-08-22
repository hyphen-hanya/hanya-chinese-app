package com.hanyachinese.app.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat

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

    /** ES2022 polyfill：老设备 System WebView(<v92) 不支持 .at()/toReversed()/replaceAll() 等新方法会红条报错，这里注入兼容实现。
     *  新设备自身支持，polyfill 仅存在时才定义，零开销；App 内 WebView 生效，不影响网站其他访问者。 */
    private val es2022Polyfill: String = """
        (function () {
            // Array.prototype.at / TypedArray.prototype.at
            if (!Array.prototype.at) {
                Object.defineProperty(Array.prototype, 'at', {
                    value: function (n) {
                        n = Math.trunc(n) || 0;
                        if (n < 0) n += this.length;
                        if (n < 0 || n >= this.length) return undefined;
                        return this[n];
                    },
                    writable: true, configurable: true
                });
            }
            // Object.hasOwn 增强
            if (!Object.hasOwn) {
                Object.hasOwn = function (obj, key) {
                    return Object.prototype.hasOwnProperty.call(obj, key);
                };
            }
            // String.prototype.replaceAll (V82+)
            if (!String.prototype.replaceAll) {
                String.prototype.replaceAll = function (search, replace) {
                    return this.split(search).join(replace);
                };
            }
            // Array.prototype.findLast (ES2023, 低版本WebView常见)
            if (!Array.prototype.findLast) {
                Array.prototype.findLast = function (pred) {
                    for (var i = this.length - 1; i >= 0; i--) {
                        if (pred(this[i], i, this)) return this[i];
                    }
                    return undefined;
                };
            }
            // Array.prototype.toReversed (ES2023 只读方法)
            if (!Array.prototype.toReversed) {
                Array.prototype.toReversed = function () {
                    return this.slice().reverse();
                };
            }
        })();
    """.trimIndent()

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
        // 防止录音blob上传/WebSocket被静默拦截: 兼容模式而非禁止(国产ROM在WebView层易静默关音轨)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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
                val isAudio = resources.orEmpty().contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (isAudio) {
                    // 音频请求: 先确认系统已授权RECORD_AUDIO, 再回主线程grant
                    val granted = context?.let {
                        ContextCompat.checkSelfPermission(
                            it, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    } == true
                    if (granted) {
                        post { request.grant(resources) }
                    } else {
                        post {
                            try { request.deny() } catch (_: Exception) {}
                        }
                        // 让 MainActivity 预请求系统权限(页面getUserMedia触发时App层面已授权则此处会grant)
                        // 若系统未授权, 这里deny后页面会提示, 且MainActivity.onResume可再主动请求
                    }
                } else {
                    post {
                        try { request.grant(resources) } catch (_: Exception) {}
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                super.onPermissionRequestCanceled(request)
            }
        }
    }

    private fun configureWebViewClient() {
        webViewClient = object : WebViewClient() {
            // 抢占注入 ES2022 polyfill：必须在页面 JS 执行前完成，否则老设备红条先出现
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.evaluateJavascript(es2022Polyfill, null)
            }

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
