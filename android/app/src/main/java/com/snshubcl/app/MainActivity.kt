package com.snshubcl.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("snshub_prefs", Context.MODE_PRIVATE) }
    private var currentFeedItem: ContentCrawler.FeedItem? = null
    
    private val hookingTemplates = listOf(
        "🚀 필독 정보! %s\n지금 바로 확인!\n👉 %s",
        "🔥 모두가 다 아는 놓치면 안 될 정보: %s\n지금 바로 클릭!\n🔗 %s",
        "💎 강추 콘텐츠! %s\n자세히 보기\n🌐 %s",
        "📈 오늘 Hot한 트렌드: %s\n지금 확인하세요!\n✅ %s",
        "✨ 너만 모르는 소식: %s\n함께 나눠요.\n📍 %s"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupContentSync()
        setupGlobalConfig()
        setupPlatformsList()
    }

    private fun setupContentSync() {
        val container = findViewById<LinearLayout>(R.id.list)
        val syncView = LayoutInflater.from(this).inflate(R.layout.item_content_sync, container, false)
        
        val preview = syncView.findViewById<TextView>(R.id.textContentPreview)
        val hooking = syncView.findViewById<TextView>(R.id.textHookingExample)
        val btnCopy = syncView.findViewById<MaterialButton>(R.id.btnCopyContent)
        val btnNext = syncView.findViewById<MaterialButton>(R.id.btnNextContent)
        val btnRefreshAll = syncView.findViewById<MaterialButton>(R.id.btnRefreshAll)

        val btnSources = mapOf(
            "Blog Dica" to syncView.findViewById<MaterialButton>(R.id.btnSourceBlog1),
            "Blog MacD" to syncView.findViewById<MaterialButton>(R.id.btnSourceBlog2),
            "Youtube 1" to syncView.findViewById<MaterialButton>(R.id.btnSourceYt1),
            "Youtube 2" to syncView.findViewById<MaterialButton>(R.id.btnSourceYt2)
        )

        fun updateHooking(item: ContentCrawler.FeedItem) {
            currentFeedItem = item
            val template = hookingTemplates.random()
            val text = template.format(item.title, item.link)
            hooking.text = text
            preview.text = "원문: ${if(item.title.length > 25) item.title.take(25) + "..." else item.title}"
            prefs.edit().putString("current_hooking_text", text).apply()
        }

        fun loadContent(source: String, isInitial: Boolean = false) {
            btnSources.forEach { (name, btn) ->
                if (name == source) {
                    btn.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_primary))
                    btn.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
                } else {
                    btn.setBackgroundColor(Color.TRANSPARENT)
                    btn.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
                }
            }

            lifecycleScope.launch {
                val feeds = ContentCrawler.fetchFromSource(source)
                if (feeds.isNotEmpty()) {
                    updateHooking(feeds.random())
                } else if (!isInitial) {
                    Toast.makeText(this@MainActivity, "$source 데이터를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnSources.forEach { (name, btn) -> btn.setOnClickListener { loadContent(name) } }

        btnRefreshAll.setOnClickListener {
            Toast.makeText(this, "실시간 데이터 동기화 시퀀스 가동 중...", Toast.LENGTH_SHORT).show()
            loadContent("Blog Dica")
        }

        btnNext.setOnClickListener {
            val sources = btnSources.keys.toList()
            loadContent(sources.random())
        }

        btnCopy.setOnClickListener {
            copyToClipboard("Hooking", hooking.text.toString())
            Toast.makeText(this, "문구와 링크가 복사되었습니다.", Toast.LENGTH_SHORT).show()
        }

        loadContent("Blog Dica", isInitial = true)
        container.addView(syncView, 0)
    }

    private fun setupGlobalConfig() {
        val modeToggle = findViewById<MaterialButtonToggleGroup>(R.id.modeToggleGroup)
        modeToggle.check(if (prefs.getBoolean("is_auto_mode", true)) R.id.btnModeAuto else R.id.btnModeManual)
        updateModeColors(modeToggle)
        
        modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.edit().putBoolean("is_auto_mode", checkedId == R.id.btnModeAuto).apply()
            updateModeColors(modeToggle)
        }

        val intervalToggle = findViewById<MaterialButtonToggleGroup>(R.id.intervalToggleGroup)
        val fixedLayout = findViewById<LinearLayout>(R.id.fixedIntervalLayout)
        val editFixed = findViewById<EditText>(R.id.editFixedSeconds)
        
        val isRandom = prefs.getBoolean("interval_random", true)
        intervalToggle.check(if (isRandom) R.id.btnRandomInterval else R.id.btnFixedInterval)
        fixedLayout.visibility = if (isRandom) View.GONE else View.VISIBLE
        updateIntervalColors(intervalToggle)
        
        intervalToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val random = checkedId == R.id.btnRandomInterval
            prefs.edit().putBoolean("interval_random", random).apply()
            fixedLayout.visibility = if (random) View.GONE else View.VISIBLE
            updateIntervalColors(intervalToggle)
        }

        editFixed.setText(prefs.getInt("fixed_interval", 10).toString())
        editFixed.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putInt("fixed_interval", s.toString().toIntOrNull() ?: 10).apply()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        setupCheckBox(R.id.checkAutoFriend, "auto_friend", true)
        setupCheckBox(R.id.checkAutoLike, "auto_like", true)
        setupCheckBox(R.id.checkAutoComment, "auto_comment", false)
    }

    private fun updateModeColors(group: MaterialButtonToggleGroup) {
        val autoColor = ContextCompat.getColor(this, R.color.accent_primary)
        val manualColor = ContextCompat.getColor(this, R.color.accent_orange)
        val dark = ContextCompat.getColor(this, R.color.bg_dark)
        
        val btnAuto = group.findViewById<MaterialButton>(R.id.btnModeAuto)
        val btnManual = group.findViewById<MaterialButton>(R.id.btnModeManual)
        
        if (btnAuto.isChecked) {
            btnAuto.setBackgroundColor(autoColor); btnAuto.setTextColor(dark)
            btnManual.setBackgroundColor(Color.TRANSPARENT); btnManual.setTextColor(manualColor)
        } else {
            btnAuto.setBackgroundColor(Color.TRANSPARENT); btnAuto.setTextColor(autoColor)
            btnManual.setBackgroundColor(manualColor); btnManual.setTextColor(dark)
        }
    }

    private fun updateIntervalColors(group: MaterialButtonToggleGroup) {
        val randomColor = ContextCompat.getColor(this, R.color.accent_indigo)
        val fixedColor = ContextCompat.getColor(this, R.color.accent_cyan)
        val dark = ContextCompat.getColor(this, R.color.bg_dark)
        
        val btnRandom = group.findViewById<MaterialButton>(R.id.btnRandomInterval)
        val btnFixed = group.findViewById<MaterialButton>(R.id.btnFixedInterval)
        
        if (btnRandom.isChecked) {
            btnRandom.setBackgroundColor(randomColor); btnRandom.setTextColor(dark)
            btnFixed.setBackgroundColor(Color.TRANSPARENT); btnFixed.setTextColor(fixedColor)
        } else {
            btnRandom.setBackgroundColor(Color.TRANSPARENT); btnRandom.setTextColor(randomColor)
            btnFixed.setBackgroundColor(fixedColor); btnFixed.setTextColor(dark)
        }
    }

    private fun setupCheckBox(id: Int, key: String, default: Boolean) {
        findViewById<CheckBox>(id).apply {
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
        }
    }

    private fun setupPlatformsList() {
        val container = findViewById<LinearLayout>(R.id.list)
        val inflater = LayoutInflater.from(this)

        for (net in Sns.ALL) {
            val item = inflater.inflate(R.layout.item_sns, container, false)
            item.findViewById<TextView>(R.id.snsName).text = net.name
            item.findViewById<TextView>(R.id.snsIcon).text = when(net.id) {
                "facebook" -> "🔵"
                "threads" -> "⚪"
                "instagram" -> "📸"
                "linkedin" -> "👔"
                "x" -> "🐦"
                else -> "🌐"
            }
            
            val langToggle = item.findViewById<MaterialButtonToggleGroup>(R.id.langToggle)
            val savedLang = prefs.getString("lang_${net.id}", "ko") ?: "ko"
            langToggle.check(if (savedLang == "en") R.id.btnLangEn else R.id.btnLangKo)
            
            updateToggleColors(langToggle)

            val textExample = item.findViewById<TextView>(R.id.textCommentExample)
            
            fun refreshComment(lang: String) {
                val list = if (lang == "en") CommentDB.EN else CommentDB.KO
                val picked = list.random()
                textExample.text = picked
                prefs.edit().putString("custom_comment_${net.id}", picked).apply()
            }
            
            refreshComment(savedLang)

            langToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val newLang = if (checkedId == R.id.btnLangEn) "en" else "ko"
                prefs.edit().putString("lang_${net.id}", newLang).apply()
                refreshComment(newLang)
                updateToggleColors(langToggle)
            }

            item.findViewById<MaterialButton>(R.id.btnRefreshComment).setOnClickListener {
                refreshComment(if (langToggle.checkedButtonId == R.id.btnLangEn) "en" else "ko")
            }
            
            item.findViewById<MaterialButton>(R.id.btnCopyComment).setOnClickListener {
                copyToClipboard("Comment", textExample.text.toString())
                Toast.makeText(this, "댓글이 복사되었습니다.", Toast.LENGTH_SHORT).show()
            }

            item.findViewById<MaterialButton>(R.id.btnWeb).setOnClickListener {
                copyToClipboard("SNS", textExample.text.toString())
                startActivity(Intent(this, WebActivity::class.java).apply { putExtra("sns", net.id) })
            }
            
            item.findViewById<MaterialButton>(R.id.btnApp).setOnClickListener {
                copyToClipboard("SNS", textExample.text.toString())
                openNativeSplit(net)
            }

            container.addView(item)
        }
    }

    private fun updateToggleColors(group: MaterialButtonToggleGroup) {
        val blue = ContextCompat.getColor(this, R.color.accent_blue)
        val dark = ContextCompat.getColor(this, R.color.bg_dark)
        
        for (i in 0 until group.childCount) {
            val btn = group.getChildAt(i) as MaterialButton
            if (btn.isChecked) {
                btn.setBackgroundColor(blue)
                btn.setTextColor(dark)
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(blue)
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun openNativeSplit(net: Sns.Net) {
        val launchIntent = packageManager.getLaunchIntentForPackage(net.pkg)
        if (launchIntent != null) {
            prefs.edit().putString("active_sns_id", net.id).apply()
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${net.pkg}")))
        }
    }
}
