package com.snshubcl.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class WebActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var logText: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var btnAuto: MaterialButton
    private lateinit var net: Sns.Net
    
    private var isAutoRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("snshub_prefs", MODE_PRIVATE) }
    private val logQueue = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        net = Sns.byId(intent.getStringExtra("sns"))

        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        logText = findViewById(R.id.logText)
        scrollLog = findViewById(R.id.scrollLog)
        btnAuto = findViewById(R.id.btnAuto)

        findViewById<TextView>(R.id.title).text = net.name
        
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        btnAuto.setOnClickListener { toggleAutomation() }
        findViewById<MaterialButton>(R.id.btnReload).setOnClickListener { web.reload() }

        setupWeb()
        
        val lang = prefs.getString("lang_${net.id}", "ko") ?: "ko"
        web.loadUrl(net.exploreUrl, mapOf("Accept-Language" to Sns.acceptLanguage(lang)))
        
        addLog("SYSTEM: 엔진 대기 중...")
        
        if (!prefs.getBoolean("is_auto_mode", true)) {
            btnAuto.text = "작업 완료 (NEXT)"
            btnAuto.setBackgroundColor(Color.parseColor("#10B981"))
            addLog("MODE: 반자동 제어 활성")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        web.addJavascriptInterface(AutomationBridge(), "AndroidAutomation")

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                addLog("ENGINE: 싱크 완료")
                injectScript()
            }
        }
        
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let { addLog("JS_LOG: ${it.message()}") }
                return true
            }
        }
    }

    private fun injectScript() {
        try {
            val script = assets.open("automation.js").bufferedReader().use { it.readText() }
            web.evaluateJavascript(script, null)
            updateJsConfig()
        } catch (e: Exception) {
            addLog("ERROR: 스크립트 로드 실패")
        }
    }

    private fun updateJsConfig() {
        val config = JSONObject().apply {
            put("isRunning", isAutoRunning)
            put("lang", prefs.getString("lang_${net.id}", "ko"))
            put("autoFriend", prefs.getBoolean("auto_friend", true))
            put("autoLike", prefs.getBoolean("auto_like", true))
            put("autoComment", prefs.getBoolean("auto_comment", false))
            put("customComment", prefs.getString("custom_comment_${net.id}", ""))
        }
        val sel = net.selectors
        val selectors = JSONObject().apply {
            put("friend", JSONArray(sel.friend))
            put("like", JSONArray(sel.like))
            put("comment", JSONArray(sel.comment))
            put("commentInput", JSONArray(sel.commentInput))
            put("commentSubmit", JSONArray(sel.commentSubmit))
        }
        web.evaluateJavascript("window.updateConfig('$config', '$selectors');", null)
    }

    private fun toggleAutomation() {
        if (!prefs.getBoolean("is_auto_mode", true)) {
            onManualWorkDone()
            return
        }

        isAutoRunning = !isAutoRunning
        if (isAutoRunning) {
            btnAuto.text = "중단"
            btnAuto.setBackgroundColor(Color.RED)
            addLog("ACTION: 자동화 시작")
            updateJsConfig()
            // 지연 후 첫 단계 실행
            handler.postDelayed({ executeStep() }, 2000)
        } else {
            btnAuto.text = "자동화 시작"
            btnAuto.setBackgroundColor(Color.parseColor("#6366F1"))
            addLog("ACTION: 사용자 중단")
            handler.removeCallbacksAndMessages(null)
            updateJsConfig()
        }
    }

    private fun executeStep() {
        if (!isAutoRunning) return
        web.evaluateJavascript("window.runAutomationStep();", null)
    }

    private fun onManualWorkDone() {
        AutomationStatsManager.incrementCount(this, net.id, "action")
        addLog("SUCCESS: 수동 작업 기록")
        Toast.makeText(this, "작업 완료 기록됨", Toast.LENGTH_SHORT).show()
        web.reload()
    }

    private fun addLog(msg: String) {
        runOnUiThread {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logQueue.addFirst("[$time] $msg")
            if (logQueue.size > 80) logQueue.removeLast()
            logText.text = logQueue.joinToString("\n")
        }
    }

    inner class AutomationBridge {
        @JavascriptInterface
        fun log(msg: String) = addLog("JS: $msg")

        @JavascriptInterface
        fun onStepFinished(success: Boolean) {
            if (success) {
                AutomationStatsManager.incrementCount(this@WebActivity, net.id, "action")
            }
            if (isAutoRunning) {
                val delay = if (prefs.getBoolean("interval_random", true)) {
                    (8000..15000).random().toLong()
                } else {
                    prefs.getInt("fixed_interval", 10) * 1000L
                }
                addLog("WAIT: 다음 작업 대기 (${delay/1000}초)")
                handler.postDelayed({ executeStep() }, delay)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
