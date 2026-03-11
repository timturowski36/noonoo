package sources.tagesschau.api

import sources.tagesschau.model.TagesschauArticle
import sources.tagesschau.model.TagesschauFeed
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TagesschauRssClient {

    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

    fun fetchArticles(feed: TagesschauFeed): Result<List<TagesschauArticle>> {
        return try {
            val connection = URL(feed.url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/rss+xml")
            connection.setRequestProperty("User-Agent", "FeedKrake/1.0")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val xml = connection.inputStream.bufferedReader().readText()
                Result.success(parseRss(xml))
            } else {
                Result.failure(Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRss(xml: String): List<TagesschauArticle> {
        val articles = mutableListOf<TagesschauArticle>()
        val itemPattern = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)

        itemPattern.findAll(xml).forEach { matchResult ->
            val itemXml = matchResult.groupValues[1]
            val article = parseItem(itemXml)
            if (article != null) {
                articles.add(article)
            }
        }

        return articles
    }

    private fun parseItem(itemXml: String): TagesschauArticle? {
        val title = extractTag(itemXml, "title") ?: return null
        val link = extractTag(itemXml, "link") ?: return null
        val description = extractTag(itemXml, "description") ?: ""
        val pubDateStr = extractTag(itemXml, "pubDate")
        val guid = extractTag(itemXml, "guid") ?: link
        val category = extractTag(itemXml, "category")

        val pubDate = pubDateStr?.let { parseDate(it) } ?: Instant.now()

        return TagesschauArticle(
            title = cleanHtml(title),
            link = link,
            description = cleanHtml(description),
            pubDate = pubDate,
            guid = guid,
            category = category
        )
    }

    private fun extractTag(xml: String, tagName: String): String? {
        val pattern = Regex("<$tagName[^>]*><!\\[CDATA\\[(.+?)]]></$tagName>|<$tagName[^>]*>(.+?)</$tagName>", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(xml) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    private fun parseDate(dateStr: String): Instant {
        return try {
            ZonedDateTime.parse(dateStr, dateFormatter).toInstant()
        } catch (e: Exception) {
            Instant.now()
        }
    }

    private fun cleanHtml(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()
    }
}
