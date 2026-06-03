package com.snshubcl.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL

object ContentCrawler {

    data class FeedItem(val title: String, val link: String, val sourceName: String)

    private val RSS_SOURCES = mapOf(
        "Blog Dica" to "https://rss.blog.naver.com/dicajohn.xml",
        "Blog MacD" to "https://rss.blog.naver.com/macdcross.xml",
        "Youtube 1" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCvW8O0z2m3L7zI6C3U_Y_PA", // @KreativeTrip (Verified)
        "Youtube 2" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCjKTu-YcMThdMZ0LggSVPPUTVpFj7GziF" // @aki6823 (Verified)
    )

    suspend fun fetchFromSource(sourceKey: String): List<FeedItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FeedItem>()
        val urlString = RSS_SOURCES[sourceKey] ?: return@withContext emptyList()
        
        try {
            val url = URL(urlString)
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(url.openStream(), "UTF-8")

            var eventType = parser.eventType
            var currentTitle = ""
            var currentLink = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                if (eventType == XmlPullParser.START_TAG) {
                    when (name) {
                        "title" -> {
                            val text = try { parser.nextText() } catch(e: Exception) { "" }
                            if (currentTitle.isEmpty()) currentTitle = text
                        }
                        "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null) {
                                currentLink = href
                            } else {
                                currentLink = try { parser.nextText() } catch(e: Exception) { "" }
                            }
                        }
                        "yt:videoId" -> {
                            val videoId = try { parser.nextText() } catch(e: Exception) { "" }
                            if (videoId.isNotBlank()) {
                                currentLink = "https://www.youtube.com/watch?v=$videoId"
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (name == "entry" || name == "item") {
                        if (currentTitle.isNotBlank() && currentLink.isNotBlank()) {
                            if (!isMetaTitle(currentTitle)) {
                                results.add(FeedItem(currentTitle.trim(), cleanUrl(currentLink), sourceKey))
                            }
                        }
                        currentTitle = ""
                        currentLink = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results.distinctBy { it.link }
    }

    private fun cleanUrl(url: String): String {
        var cleaned = url
        if (cleaned.contains("?")) {
            val base = cleaned.substringBefore("?")
            if (cleaned.contains("blog.naver.com")) {
                cleaned = base
            } else if (cleaned.contains("youtube.com/watch")) {
                val regex = Regex("v=([a-zA-Z0-9_-]+)")
                val match = regex.find(cleaned)
                cleaned = if (match != null) "$base?${match.value}" else base
            }
        }
        return cleaned
    }

    private fun isMetaTitle(title: String): Boolean {
        val metaKeywords = listOf("Blog", "YouTube", "네이버 블로그", "dicajohn", "macdcross", "KreativeTrip", "aki6823")
        return metaKeywords.any { title.equals(it, ignoreCase = true) || title.contains("RSS") }
    }
}
