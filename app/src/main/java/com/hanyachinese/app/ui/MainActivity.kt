package com.hanyachinese.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.hanyachinese.app.R
import com.hanyachinese.app.web.AppWebView

/**
 * Hanya Chinese 语言学习 App 主界面
 *
 * 单 WebView 全屏加载 https://lang.hanyabuy.com
 * 站点自带底部导航(Home/Learn/Teach/Me), App 不重复套壳
 *
 * 原生增强:
 *  - 麦克风权限(跟读发音)
 *  - 返回键后退导航(不是退出App)
 *  - 下拉刷新
 *  - 原生分享当前页(URL)
 *  - 加载进度条
 *  - 深链: lang.hanyabuy.com/xxx 直接打开
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: AppWebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val HOME_URL = "https://lang.hanyabuy.com"

    // 麦克风运行时权限(只进语音/跟读时预请求一次, 由页面 getUserMedia 触发)
    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 授权与否前端有兜底 */ }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                // 无法后退则最小化(不退出, 保持学习状态)
                moveTaskToBack(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById<AppWebView>(R.id.web_view)
        progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)

        setupProgressBar()
        setupSwipeRefresh()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // 深链: 从 lang.hanyabuy.com/xxx 打开则加载对应路径, 否则首页
        val startUrl = intent?.data?.toString()
            ?.takeIf { it.startsWith("https://lang.hanyabuy.com") }
            ?: HOME_URL
        if (savedInstanceState == null) {
            webView.loadUrl(startUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    /** 加载进度条: 通过 AppWebView 进度回调显示顶部进度条 */
    private fun setupProgressBar() {
        webView.onProgressChanged = { newProgress ->
            progressBar.progress = newProgress
            progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            webView.reload()
            swipeRefresh.isRefreshing = false
        }
        // 站点内含自己 ContentScrollView, 下拉刷新用整页(简化处理)
        swipeRefresh.setColorSchemeResources(R.color.primary_blue)
    }

    /** 预请求麦克风(供页面语音录入用) */
    fun requestMicIfPresent() {
        val perm = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(perm)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
        webView.abandonAudioFocus()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
