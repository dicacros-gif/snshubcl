package com.snshubcl.app

object Sns {

    data class Net(
        val id: String,
        val name: String,
        val accent: String,
        val webUrl: String,
        val exploreUrl: String,
        val pkg: String,
        val selectors: Selectors,
        val dailyLimit: Int
    )

    data class Selectors(
        val friend: Array<String>,
        val like: Array<String>,
        val comment: Array<String>,
        val commentInput: Array<String>,
        val commentSubmit: Array<String>
    )

    val ALL = listOf(
        Net(
            "facebook", "Facebook", "#1877F2", 
            "https://m.facebook.com/",
            "https://m.facebook.com/friends/center/suggestions", 
            "com.facebook.katana",
            Selectors(
                friend = arrayOf("button[aria-label*='친구 추가']", "div[role='button']:has(span:contains('친구 추가'))", "button:contains('추가')"),
                like = arrayOf("div[role='button'][aria-label='좋아요']", "div.u_likeit_list_btn", "div[aria-label='Like']"),
                comment = arrayOf("div[aria-label='댓글 달기']", "div[role='button']:has(span:contains('댓글'))"),
                commentInput = arrayOf("textarea[aria-label*='댓글']", "div[role='textbox']", "input[type='text']"),
                commentSubmit = arrayOf("div[aria-label*='게시']", "button:contains('게시')")
            ), 50
        ),
        Net(
            "instagram", "Instagram", "#E1306C", 
            "https://www.instagram.com/",
            "https://www.instagram.com/explore/people/", 
            "com.instagram.android",
            Selectors(
                friend = arrayOf("button:contains('팔로우')", "div[role='button']:contains('팔로우')", "button._acan"),
                like = arrayOf("span[aria-label='좋아요']", "svg[aria-label='좋아요']", "div[role='button'] svg[aria-label='좋아요']"),
                comment = arrayOf("span[aria-label='댓글 달기']", "div[role='button'] svg[aria-label='댓글']"),
                commentInput = arrayOf("textarea[aria-label*='댓글']", "textarea[placeholder*='댓글']"),
                commentSubmit = arrayOf("div[role='button']:contains('게시')", "button:contains('게시')")
            ), 50
        ),
        Net(
            "threads", "Threads", "#FFFFFF", 
            "https://www.threads.net/",
            "https://www.threads.net/search", 
            "com.instagram.barcelona",
            Selectors(
                friend = arrayOf("div[role='button']:has(div:contains('팔로우'))", "button:contains('팔로우')"),
                like = arrayOf("div[role='button'][aria-label*='좋아요']"),
                comment = arrayOf("div[role='button'][aria-label*='답글']"),
                commentInput = arrayOf("div[role='textbox']"),
                commentSubmit = arrayOf("div[role='button']:contains('게시')")
            ), 50
        ),
        Net(
            "linkedin", "LinkedIn", "#0A66C2", 
            "https://www.linkedin.com/",
            "https://www.linkedin.com/mynetwork/", 
            "com.linkedin.android",
            Selectors(
                friend = arrayOf("button:contains('1촌 신청')", "button:contains('Connect')", "button[aria-label*='1촌 신청']"),
                like = arrayOf("button[aria-label*='좋아요']", "button.react-button__trigger"),
                comment = arrayOf("button[aria-label*='댓글']", "button.comment-button"),
                commentInput = arrayOf("div[role='textbox']", "div.ql-editor"),
                commentSubmit = arrayOf("button.comments-comment-box__submit-button")
            ), 20
        ),
        Net(
            "x", "X", "#FFFFFF", 
            "https://x.com/",
            "https://x.com/i/connect_people", 
            "com.twitter.android",
            Selectors(
                friend = arrayOf("div[role='button']:has(span:contains('팔로우'))", "div[data-testid*='follow']"),
                like = arrayOf("div[data-testid='like']", "div[role='button'][aria-label*='좋아요']"),
                comment = arrayOf("div[data-testid='reply']", "div[role='button'][aria-label*='답글']"),
                commentInput = arrayOf("div[data-testid='tweetTextarea_0']", "div[role='textbox']"),
                commentSubmit = arrayOf("div[data-testid='tweetButtonInline']", "button:contains('게시')")
            ), 100
        )
    )

    fun byId(id: String?): Net = ALL.firstOrNull { it.id == id } ?: ALL[0]

    fun acceptLanguage(lang: String): String =
        if (lang == "en") "en-US,en;q=0.9" else "ko-KR,ko;q=0.9,en;q=0.6"
}
