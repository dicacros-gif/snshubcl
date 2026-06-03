package com.snshubcl.app

object ContentSource {
    data class Item(
        val title: String,
        val url: String
    )

    // 실제 크롤링된 데이터를 보관할 변수
    var cachedItems = mutableListOf<Item>()

    fun getLatestOrRandom(): Item {
        return if (cachedItems.isNotEmpty()) cachedItems.random() 
               else Item("데이터 로드 중...", "https://m.naver.com")
    }
}
