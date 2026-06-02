package com.snshubcl.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * 앱 내 브라우저 화면.
 *
 * 평범한 WebView 브라우저다. 쿠키를 유지해 로그인 세션이 살아있고, 뒤로/앞으로/새로고침과
 * 파일 업로드(프로필 사진 등 정상적인 수동 사용)를 지원한다.
 *
 * 의도적으로 넣지 않은 것:
 *  - addJavascriptInterface (JS→앱 브리지)  ← 없음
 *  - 페이지에 주입하는 자동 클릭/입력 스크립트 ← 없음
 *  - 동작 큐, 딜레이, 랜덤 타이밍            ← 없음
 *  - confirm/alert 자동 통과                ← 없음 (사용자가 직접 확인)
 * 즉 자동화/탐지 회피 요소가 전혀 없는 순수 브라우저다.
 */
class WebActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var net: Sns.Net
    private var lang: String = "ko"

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooser: ActivityResultLauncher<Intent>

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        net = Sns.byId(intent.getStringExtra("sns"))
        lang = intent.getStringExtra("lang") ?: "ko"

        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)

        val accent = Color.parseColor(net.accent)
        findViewById<TextView>(R.id.title).apply {
            text = net.name
            setTextColor(accent)
        }

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<MaterialButton>(R.id.btnFwd).setOnClickListener { if (web.canGoForward()) web.goForward() }
        findViewById<MaterialButton>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<MaterialButton>(R.id.btnOpenApp).setOnClickListener { openNativeApp(net.pkg) }

        fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = filePathCallback
            filePathCallback = null
            cb?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
        }

        setupWeb()
        web.loadUrl(net.webUrl, mapOf("Accept-Language" to Sns.acceptLanguage(lang)))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)

        web.settings.apply {
            javaScriptEnabled = true          // 사이트가 동작하려면 필요 — 우리가 주입하는 스크립트는 없음
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString =
                "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme ?: ""
                if (scheme == "http" || scheme == "https") return false  // 웹뷰 내부에서 로드
                // intent:, market:, mailto:, tel: 등은 외부 앱에 위임
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url)); true
                } catch (e: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                CookieManager.getInstance().flush()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
            }

            override fun onShowFileChooser(
                view: WebView?,
                cb: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                val intent = params?.createIntent() ?: return false
                filePathCallback?.onReceiveValue(null)
                filePathCallback = cb
                return try {
                    fileChooser.launch(intent); true
                } catch (e: Exception) {
                    filePathCallback = null; false
                }
            }
        }
    }

    private fun openNativeApp(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
