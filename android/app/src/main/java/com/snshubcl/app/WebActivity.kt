package com.snshubcl.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * 인앱 브라우저. 로그인 세션(쿠키) 유지 + 파일 업로드 + 계정 전환.
 * 자동화/JS 주입은 전혀 없다 — 그냥 브라우저다.
 *
 * intent extras:
 *   sns   : 플랫폼 id (Accept-Language, 기본 홈 URL 결정)
 *   url   : (선택) 띄울 URL(작성 화면 등). 없으면 해당 플랫폼 홈.
 *   title : (선택) 상단 제목.
 *   seq   : (선택) 순차 공유 중인지 — '다음 ▶' 라벨 표시.
 */
class WebActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var net: Sns.Net
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        fileCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        net = Sns.byId(intent.getStringExtra("sns"))
        val startUrl = intent.getStringExtra("url") ?: net.webUrl
        val titleText = intent.getStringExtra("title") ?: net.name
        val composeMode = intent.hasExtra("url")
        val seq = intent.getBooleanExtra("seq", false)

        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        findViewById<TextView>(R.id.title).text = titleText
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            if (web.canGoBack()) web.goBack() else finish()
        }
        findViewById<MaterialButton>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<MaterialButton>(R.id.btnSwitch).setOnClickListener { switchAccount() }
        findViewById<MaterialButton>(R.id.btnDone).apply {
            if (composeMode) {
                visibility = View.VISIBLE
                text = if (seq) "다음 ▶" else "완료"
                setOnClickListener { finish() }
            } else {
                visibility = View.GONE
            }
        }

        setupWeb()
        web.loadUrl(startUrl, langHeaders())
    }

    private fun langHeaders(): Map<String, String> {
        val lang = getSharedPreferences("snshub_prefs", MODE_PRIVATE).getString("lang_${net.id}", "ko") ?: "ko"
        return mapOf("Accept-Language" to Sns.acceptLanguage(lang))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = params?.createIntent()
                if (intent == null) {
                    fileCallback = null
                    return false
                }
                return try {
                    fileChooser.launch(intent)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }
    }

    /** 계정 전환: 인앱 브라우저 세션(쿠키/스토리지)을 비우고 로그인 화면으로 이동. */
    private fun switchAccount() {
        AlertDialog.Builder(this)
            .setTitle("계정 전환")
            .setMessage("인앱 브라우저의 로그인 세션을 모두 해제하고 ${net.name} 로그인 화면으로 이동합니다. 계속할까요?")
            .setPositiveButton("로그아웃") { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                web.clearHistory()
                web.clearCache(true)
                web.loadUrl(net.webUrl, langHeaders())
            }
            .setNegativeButton("취소", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
