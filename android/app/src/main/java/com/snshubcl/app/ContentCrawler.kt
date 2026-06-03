package com.snshubcl.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL

/**
 * 내 블로그/유튜브 RSS(Atom)를 읽어 글 목록을 가져온다.
 *
 * 파싱 원칙: title·link·yt:videoId·published 는 <entry>/<item> **안에 있을 때만** 읽는다.
 * (피드 최상위 <title>(채널명)을 영상 제목과 섞어 읽어 최신 글이 누락되던 버그 수정)
 * 항목 순서는 피드 그대로(최신이 먼저) 유지하므로 first() = 최신.
 */
object ContentCrawler {

    data class FeedItem(
        val title: String,
        val link: String,
        val sourceName: String,
        val published: String = ""
    )

    val SOURCE_KEYS = listOf("Blog Dica", "Blog MacD", "Youtube 1", "Youtube 2")

    private val RSS_SOURCES = mapOf(
        "Blog Dica" to "https://rss.blog.naver.com/dicajohn.xml",
        "Blog MacD" to "https://rss.blog.naver.com/macdcross.xml",
        // 핸들에서 해석한 정확한 channel_id (RSS는 @핸들이 아니라 channel_id 필요)
        "Youtube 1" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCAswZxbVw8QfcjN3f2rO8og", // @KreativeTrip
        "Youtube 2" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCsgea8JdRXlZluEdNzqWb-Q"  // @aki6823
    )

    suspend fun fetchFromSource(sourceKey: String): List<FeedItem> = withContext(Dispatchers.IO) {
        val urlString = RSS_SOURCES[sourceKey] ?: return@withContext emptyList()
        val results = mutableListOf<FeedItem>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            URL(urlString).openStream().use { stream ->
                parser.setInput(stream, "UTF-8")
                var event = parser.eventType
                var inItem = false
                var title = ""
                var link = ""
                var published = ""

                while (event != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (event) {
                        XmlPullParser.START_TAG -> when (name) {
                            "entry", "item" -> {
                                inItem = true; title = ""; link = ""; published = ""
                            }
                            "title" -> if (inItem && title.isEmpty()) {
                                title = readText(parser)
                            }
                            "link" -> if (inItem) {
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null) {
                                    if (link.isEmpty()) link = href          // Atom: rel="alternate"
                                } else {
                                    val t = readText(parser)                 // RSS: <link>text</link>
                                    if (t.isNotBlank()) link = t
                                }
                            }
                            // yt:videoId — 네임스페이스 비활성 시 "yt:videoId", 활성 시 "videoId"
                            "yt:videoId", "videoId" -> if (inItem) {
                                val v = readText(parser)
                                if (v.isNotBlank()) link = "https://www.youtube.com/watch?v=$v"
                            }
                            "published", "pubDate" -> if (inItem && published.isEmpty()) {
                                published = readText(parser)
                            }
                        }
                        XmlPullParser.END_TAG -> if (name == "entry" || name == "item") {
                            if (title.isNotBlank() && link.isNotBlank()) {
                                results.add(FeedItem(title.trim(), cleanUrl(link), sourceKey, published))
                            }
                            inItem = false
                        }
                    }
                    event = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results.distinctBy { it.link }
    }

    private fun readText(parser: XmlPullParser): String =
        try { parser.nextText() } catch (e: Exception) { "" }

    private fun cleanUrl(url: String): String {
        if (!url.contains("?")) return url
        val base = url.substringBefore("?")
        return when {
            url.contains("blog.naver.com") -> base
            url.contains("youtube.com/watch") -> {
                val m = Regex("v=([a-zA-Z0-9_-]+)").find(url)
                if (m != null) "$base?${m.value}" else base
            }
            else -> url
        }
    }
}
