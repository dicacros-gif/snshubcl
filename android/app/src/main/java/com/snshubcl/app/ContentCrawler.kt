package com.snshubcl.app

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL

/**
 * 본인 블로그/유튜브 RSS를 읽어 글 목록을 가져온다.
 * 블로킹 함수 — 반드시 백그라운드 스레드에서 호출할 것. (코루틴 의존성 없음)
 */
object ContentCrawler {

    data class FeedItem(val title: String, val link: String, val sourceName: String)

    val SOURCE_KEYS = listOf("Blog Dica", "Blog MacD", "Youtube 1", "Youtube 2")

    private val RSS_SOURCES = mapOf(
        "Blog Dica" to "https://rss.blog.naver.com/dicajohn.xml",
        "Blog MacD" to "https://rss.blog.naver.com/macdcross.xml",
        "Youtube 1" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCvW8O0z2m3L7zI6C3U_Y_PA",
        "Youtube 2" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCjKTu-YcMThdMZ0LggSVPPUTVpFj7GziF"
    )

    fun fetchFromSource(sourceKey: String): List<FeedItem> {
        val urlString = RSS_SOURCES[sourceKey] ?: return emptyList()
        val results = mutableListOf<FeedItem>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            URL(urlString).openStream().use { stream ->
                parser.setInput(stream, "UTF-8")
                var eventType = parser.eventType
                var currentTitle = ""
                var currentLink = ""

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    if (eventType == XmlPullParser.START_TAG) {
                        when (name) {
                            "title" -> {
                                val text = try { parser.nextText() } catch (e: Exception) { "" }
                                if (currentTitle.isEmpty()) currentTitle = text
                            }
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                currentLink = if (href != null) href
                                              else try { parser.nextText() } catch (e: Exception) { "" }
                            }
                            "yt:videoId" -> {
                                val videoId = try { parser.nextText() } catch (e: Exception) { "" }
                                if (videoId.isNotBlank()) currentLink = "https://www.youtube.com/watch?v=$videoId"
                            }
                        }
                    } else if (eventType == XmlPullParser.END_TAG && (name == "entry" || name == "item")) {
                        if (currentTitle.isNotBlank() && currentLink.isNotBlank() && !isMetaTitle(currentTitle)) {
                            results.add(FeedItem(currentTitle.trim(), cleanUrl(currentLink), sourceKey))
                        }
                        currentTitle = ""
                        currentLink = ""
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results.distinctBy { it.link }
    }

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

    private fun isMetaTitle(title: String): Boolean {
        val meta = listOf("Blog", "YouTube", "네이버 블로그", "dicajohn", "macdcross", "KreativeTrip", "aki6823")
        return meta.any { title.equals(it, ignoreCase = true) } || title.contains("RSS")
    }
}
