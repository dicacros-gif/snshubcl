package com.snshubcl.app

/**
 * 지원하는 5개 SNS 정의 — 단순 데이터 + 진입 URL + 네이티브 패키지명.
 *
 * 이 앱은 "한 곳에서 열어보는" 브라우저/런처일 뿐이라, 여기 들어가는 정보는
 *  - webUrl : 앱 내 WebView 로 열 진입 주소(로그아웃 상태면 각 플랫폼 로그인 화면이 뜬다)
 *  - pkg    : '앱으로 열기'가 실행할 설치된 네이티브 앱 패키지
 * 두 가지뿐이다. 자동 클릭·신청·스크립트 주입 같은 자동화 정보는 일절 두지 않는다.
 */
object Sns {

    data class Net(
        val id: String,
        val name: String,
        val accent: String,   // 카드/제목 강조색 (다크 배경에서 보이는 색)
        val webUrl: String,   // WebView 진입 주소 (모바일 웹)
        val pkg: String       // 네이티브 앱 패키지
    )

    val ALL = listOf(
        Net("facebook",  "Facebook",  "#1877F2", "https://m.facebook.com/",    "com.facebook.katana"),
        Net("threads",   "Threads",   "#EDEDED", "https://www.threads.net/",   "com.instagram.barcelona"),
        Net("instagram", "Instagram", "#E1306C", "https://www.instagram.com/", "com.instagram.android"),
        Net("linkedin",  "LinkedIn",  "#0A66C2", "https://www.linkedin.com/",  "com.linkedin.android"),
        Net("x",         "X",         "#1D9BF0", "https://x.com/",             "com.twitter.android")
    )

    fun byId(id: String?): Net = ALL.firstOrNull { it.id == id } ?: ALL[0]

    /** WebView 진입 시 보낼 Accept-Language. 로그아웃 상태의 사이트 언어에 영향을 준다. */
    fun acceptLanguage(lang: String): String =
        if (lang == "en") "en-US,en;q=0.9" else "ko-KR,ko;q=0.9,en;q=0.6"

    fun storeUrl(pkg: String) = "https://play.google.com/store/apps/details?id=$pkg"
}
