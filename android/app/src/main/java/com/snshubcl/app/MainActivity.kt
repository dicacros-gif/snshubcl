package com.snshubcl.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * 대시보드:
 *  1) 콘텐츠 카드 — 내 블로그/유튜브 RSS를 가져와 캡션 초안을 만들고(직접 수정 가능),
 *     클립보드 복사 / 공유 시트로 내보낸다.
 *  2) 플랫폼 카드 5개 — 인앱 브라우저(웹)·네이티브 앱 열기, 그리고
 *     "이 캡션으로 공유"(각 플랫폼의 공식 작성 화면을 띄움, 게시는 사람이 직접).
 *
 * 자동 클릭/팔로우/좋아요/댓글 같은 자동화는 없다.
 */
class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("snshub_prefs", Context.MODE_PRIVATE) }

    private lateinit var editCaption: EditText
    private lateinit var textPreview: TextView
    private var currentLink: String = ""
    private var currentSource: String = ContentCrawler.SOURCE_KEYS.first()
    private val sourceButtons = mutableMapOf<String, MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val container = findViewById<LinearLayout>(R.id.list)
        setupContentCard(container)
        setupPlatformCards(container)
    }

    // ---------- 콘텐츠 카드 ----------

    private fun setupContentCard(container: LinearLayout) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_content_sync, container, false)

        editCaption = view.findViewById(R.id.editCaption)
        textPreview = view.findViewById(R.id.textContentPreview)

        sourceButtons["Blog Dica"] = view.findViewById(R.id.btnSourceBlog1)
        sourceButtons["Blog MacD"] = view.findViewById(R.id.btnSourceBlog2)
        sourceButtons["Youtube 1"] = view.findViewById(R.id.btnSourceYt1)
        sourceButtons["Youtube 2"] = view.findViewById(R.id.btnSourceYt2)
        sourceButtons.forEach { (key, btn) -> btn.setOnClickListener { loadSource(key) } }

        view.findViewById<MaterialButton>(R.id.btnRefreshAll).setOnClickListener { loadSource(currentSource) }
        view.findViewById<MaterialButton>(R.id.btnNextContent).setOnClickListener {
            loadSource(ContentCrawler.SOURCE_KEYS.random())
        }
        view.findViewById<MaterialButton>(R.id.btnCopyContent).setOnClickListener {
            copyToClipboard("SNS", shareText())
            toast("문구가 복사되었습니다.")
        }
        view.findViewById<MaterialButton>(R.id.btnShareSheet).setOnClickListener {
            if (shareText().isBlank()) { toast("먼저 콘텐츠를 가져오세요."); return@setOnClickListener }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText())
            }
            startActivity(Intent.createChooser(send, "공유"))
        }

        container.addView(view)
        loadSource(currentSource)
    }

    private fun loadSource(source: String) {
        currentSource = source
        setSourceActive(source)
        Thread {
            val feeds = ContentCrawler.fetchFromSource(source)
            runOnUiThread {
                if (feeds.isNotEmpty()) applyItem(feeds.random())
                else toast("$source 데이터를 가져올 수 없습니다.")
            }
        }.start()
    }

    private fun applyItem(item: ContentCrawler.FeedItem) {
        currentLink = item.link
        editCaption.setText(item.title)
        val shortTitle = if (item.title.length > 40) item.title.take(40) + "…" else item.title
        textPreview.text = "원문: $shortTitle\n${item.link}"
    }

    private fun setSourceActive(active: String) {
        sourceButtons.forEach { (key, btn) ->
            if (key == active) {
                btn.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_primary))
                btn.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
            }
        }
    }

    private fun currentCaption(): String = editCaption.text?.toString()?.trim().orEmpty()

    private fun shareText(): String {
        val cap = currentCaption()
        return when {
            currentLink.isBlank() -> cap
            cap.isBlank() -> currentLink
            else -> "$cap\n$currentLink"
        }
    }

    // ---------- 플랫폼 카드 ----------

    private fun setupPlatformCards(container: LinearLayout) {
        val inflater = LayoutInflater.from(this)
        for (net in Sns.ALL) {
            val item = inflater.inflate(R.layout.item_sns, container, false)
            item.findViewById<TextView>(R.id.snsName).text = net.name
            item.findViewById<TextView>(R.id.snsIcon).text = net.emoji

            val langToggle = item.findViewById<MaterialButtonToggleGroup>(R.id.langToggle)
            val savedLang = prefs.getString("lang_${net.id}", "ko") ?: "ko"
            langToggle.check(if (savedLang == "en") R.id.btnLangEn else R.id.btnLangKo)
            updateToggleColors(langToggle)
            langToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                prefs.edit().putString("lang_${net.id}", if (checkedId == R.id.btnLangEn) "en" else "ko").apply()
                updateToggleColors(langToggle)
            }

            item.findViewById<MaterialButton>(R.id.btnWeb).setOnClickListener {
                startActivity(Intent(this, WebActivity::class.java).putExtra("sns", net.id))
            }
            item.findViewById<MaterialButton>(R.id.btnApp).setOnClickListener { openNativeApp(net) }
            item.findViewById<MaterialButton>(R.id.btnShare).setOnClickListener { shareTo(net) }

            container.addView(item)
        }
    }

    /** 캡션을 해당 플랫폼의 공식 작성 화면으로 넘긴다. 게시는 사람이 직접. */
    private fun shareTo(net: Sns.Net) {
        if (currentLink.isBlank()) { toast("먼저 콘텐츠를 가져오세요."); return }
        copyToClipboard("SNS", shareText()) // 붙여넣기 백업용

        if (Sns.hasWebCompose(net.id)) {
            val url = Sns.composeUrl(net.id, currentCaption(), currentLink)
            startActivity(
                Intent(this, WebActivity::class.java)
                    .putExtra("sns", net.id)
                    .putExtra("url", url)
                    .putExtra("title", "${net.name} 작성")
            )
            toast("작성 화면을 엽니다. 검토 후 직접 게시하세요.")
        } else {
            openNativeApp(net) // Instagram 등 웹 작성 미지원
            toast("캡션이 복사되었습니다. 앱에서 붙여넣기 하세요.")
        }
    }

    private fun openNativeApp(net: Sns.Net) {
        val launch = packageManager.getLaunchIntentForPackage(net.pkg)
        if (launch != null) {
            startActivity(launch)
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${net.pkg}")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${net.pkg}")))
            }
        }
    }

    // ---------- 공통 ----------

    private fun updateToggleColors(group: MaterialButtonToggleGroup) {
        val blue = ContextCompat.getColor(this, R.color.accent_blue)
        val dark = ContextCompat.getColor(this, R.color.bg_dark)
        for (i in 0 until group.childCount) {
            val btn = group.getChildAt(i) as MaterialButton
            if (btn.isChecked) {
                btn.setBackgroundColor(blue); btn.setTextColor(dark)
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT); btn.setTextColor(blue)
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
