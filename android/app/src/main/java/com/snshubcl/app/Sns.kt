package com.snshubcl.app

import android.net.Uri

/**
 * 플랫폼 정의 + 공식 공유(작성) 엔드포인트.
 *
 * 설계 원칙: 자동화는 일절 없다. 모든 게시는 각 플랫폼의 정식 작성 화면에서
 * 사람이 직접 검토하고 누른다. (약관 준수)
 */
object Sns {

    data class Net(
        val id: String,
        val name: String,
        val emoji: String,
        val accent: String,
        val webUrl: String,   // 인앱 브라우저 홈
        val pkg: String       // 네이티브 앱 패키지
    )

    val ALL = listOf(
        Net("facebook",  "Facebook",  "🔵", "#1877F2", "https://m.facebook.com/",    "com.facebook.katana"),
        Net("instagram", "Instagram", "📸", "#E1306C", "https://www.instagram.com/", "com.instagram.android"),
        Net("threads",   "Threads",   "⚪", "#A78BFA", "https://www.threads.net/",   "com.instagram.barcelona"),
        Net("linkedin",  "LinkedIn",  "👔", "#0A66C2", "https://www.linkedin.com/",  "com.linkedin.android"),
        Net("x",         "X",         "🐦", "#60A5FA", "https://x.com/",             "com.twitter.android")
    )

    fun byId(id: String?): Net = ALL.firstOrNull { it.id == id } ?: ALL[0]

    fun acceptLanguage(lang: String): String =
        if (lang == "en") "en-US,en;q=0.9" else "ko-KR,ko;q=0.9,en;q=0.6"

    /** 웹 작성(공유) 화면을 제공하는 플랫폼인가. (Instagram 피드는 미제공) */
    fun hasWebCompose(id: String): Boolean = id != "instagram"

    /**
     * 각 플랫폼의 공식 공유/작성 URL. 글은 사람이 검토 후 직접 게시한다.
     *  - X / Threads : 본문(text)에 캡션, 링크 동봉
     *  - Facebook / LinkedIn : 공식 sharer (링크의 OG 정보로 미리보기 카드 생성)
     */
    fun composeUrl(id: String, caption: String, link: String): String {
        val u = Uri.encode(link)
        return when (id) {
            "x"        -> "https://twitter.com/intent/tweet?text=${Uri.encode(caption)}&url=$u"
            "threads"  -> "https://www.threads.net/intent/post?text=${Uri.encode("$caption $link")}"
            "facebook" -> "https://www.facebook.com/sharer/sharer.php?u=$u"
            "linkedin" -> "https://www.linkedin.com/sharing/share-offsite/?url=$u"
            else       -> link
        }
    }
}
