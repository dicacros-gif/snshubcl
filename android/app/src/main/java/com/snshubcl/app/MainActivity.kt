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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch

/**
 * 대시보드:
 *  1) 콘텐츠 카드 — 내 블로그/유튜브 RSS의 **최신** 글을 가져와 수정 가능한 캡션을 만들고,
 *     복사 / 공유 시트로 내보낸다. 네트워크 실패 시 마지막 성공본(SharedPreferences)을 보여준다.
 *  2) 플랫폼 카드 5개 — 인앱 브라우저·네이티브 앱 열기 + "이 캡션으로 공유"(공식 작성 화면).
 *
 * 자동 클릭/팔로우/좋아요/댓글 같은 자동화는 없다. 게시는 사람이 직접.
 */
class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("snshub_prefs", Context.MODE_PRIVATE) }

    private lateinit var editCaption: EditText
    private lateinit var textPreview: TextView
    private var currentLink: String = ""
    private var currentSource: String = "Youtube 1"
    private var currentFeeds: List<ContentCrawler.FeedItem> = emptyList()
    private var currentIndex: Int = 0
    private val sourceButtons = mutableMapOf<String, MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)            // UI 먼저 표시

        val container = findViewById<LinearLayout>(R.id.list)
        currentSource = prefs.getString("last_source", "Youtube 1") ?: "Youtube 1"
        setupContentCard(container)
        setupPlatformCards(container)

        loadSource(currentSource)                          // 시작 즉시 최신 가져오기(백그라운드)
        // 두 유튜브 최신을 캐시에 예열 → 탭 전환 시 즉시 표시
        listOf("Youtube 1", "Youtube 2").filter { it != currentSource }.forEach { prefetch(it) }
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
        setSourceActive(currentSource)

        view.findViewById<MaterialButton>(R.id.btnRefreshAll).setOnClickListener { loadSource(currentSource) }
        view.findViewById<MaterialButton>(R.id.btnNextContent).setOnClickListener { showNext() }
        view.findViewById<MaterialButton>(R.id.btnCopyContent).setOnClickListener {
            if (shareText().isBlank()) { toast("먼저 콘텐츠를 가져오세요."); return@setOnClickListener }
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
    }

    /** 선택 소스의 최신 글을 비동기로 가져온다. 실패 시 마지막 성공본 캐시 표시. */
    private fun loadSource(source: String) {
        currentSource = source
        prefs.edit().putString("last_source", source).apply()
        setSourceActive(source)
        lifecycleScope.launch {
            val feeds = ContentCrawler.fetchFromSource(source)
            if (feeds.isNotEmpty()) {
                currentFeeds = feeds
                currentIndex = 0
                applyItem(feeds.first())
                cacheItem(source, feeds.first())
            } else {
                val cached = cachedItem(source)
                if (cached != null) {
                    currentFeeds = listOf(cached)
                    currentIndex = 0
                    applyItem(cached)
                    toast("$source: 네트워크 실패 — 마지막 저장본 표시")
                } else {
                    toast("$source 데이터를 가져올 수 없습니다.")
                }
            }
        }
    }

    /** UI를 건드리지 않고 캐시만 갱신(예열). */
    private fun prefetch(source: String) {
        lifecycleScope.launch {
            val feeds = ContentCrawler.fetchFromSource(source)
            if (feeds.isNotEmpty()) cacheItem(source, feeds.first())
        }
    }

    /** 같은 소스 내 다음 글로 순환. */
    private fun showNext() {
        if (currentFeeds.isEmpty()) { loadSource(currentSource); return }
        currentIndex = (currentIndex + 1) % currentFeeds.size
        applyItem(currentFeeds[currentIndex])
    }

    private fun applyItem(item: ContentCrawler.FeedItem) {
        currentLink = item.link
        editCaption.setText(item.title)
        val shortTitle = if (item.title.length > 40) item.title.take(40) + "…" else item.title
        textPreview.text = "${item.sourceName} · 원문: $shortTitle\n${item.link}"
    }

    private fun cacheItem(source: String, item: ContentCrawler.FeedItem) {
        prefs.edit()
            .putString("cache_${source}_title", item.title)
            .putString("cache_${source}_link", item.link)
            .apply()
    }

    private fun cachedItem(source: String): ContentCrawler.FeedItem? {
        val t = prefs.getString("cache_${source}_title", null) ?: return null
        val l = prefs.getString("cache_${source}_link", null) ?: return null
        return ContentCrawler.FeedItem(t, l, source)
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
