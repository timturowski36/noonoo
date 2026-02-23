package sources.claude.api

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Lädt Webseiten-Inhalte und konvertiert HTML zu lesbarem Text.
 */
class WebFetcher {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // ─────────────────────────────────────────────────────────────────────────
    // Webseite laden und HTML zu Text konvertieren
    // ─────────────────────────────────────────────────────────────────────────

    fun fetch(url: String): WebContent? {
        return try {
            println("🌐 [WebFetcher] Lade $url ...")

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; FeedKrake/1.0)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() !in 200..299) {
                println("❌ [WebFetcher] HTTP ${response.statusCode()}")
                return null
            }

            val html = response.body()
            val text = htmlToText(html)
            val title = extractTitle(html)

            println("✅ [WebFetcher] ${text.length} Zeichen geladen (${title ?: "kein Titel"})")

            WebContent(
                url = url,
                title = title,
                html = html,
                text = text
            )

        } catch (e: Exception) {
            println("❌ [WebFetcher] Fehler: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML zu lesbarem Text konvertieren (einfache Variante)
    // ─────────────────────────────────────────────────────────────────────────

    private fun htmlToText(html: String): String {
        return html
            // Script und Style Blöcke entfernen
            .replace(Regex("""<script[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<style[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<head[^>]*>[\s\S]*?</head>""", RegexOption.IGNORE_CASE), "")
            // Kommentare entfernen
            .replace(Regex("""<!--[\s\S]*?-->"""), "")
            // Block-Elemente mit Zeilenumbrüchen
            .replace(Regex("""<(p|div|br|hr|h[1-6]|li|tr)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</(p|div|h[1-6]|li|tr|table)>""", RegexOption.IGNORE_CASE), "\n")
            // Alle anderen Tags entfernen
            .replace(Regex("""<[^>]+>"""), " ")
            // HTML-Entities dekodieren
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""&#(\d+);""")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
            }
            // Mehrfache Leerzeichen/Zeilenumbrüche reduzieren
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n[ \t]+"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun extractTitle(html: String): String? {
        val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE)
        return titleRegex.find(html)?.groupValues?.get(1)?.trim()
    }
}

/**
 * Geladener Webseiten-Inhalt.
 */
data class WebContent(
    val url: String,
    val title: String?,
    val html: String,
    val text: String
) {
    /**
     * Gibt einen gekürzten Text zurück (für Claude-Anfragen mit Token-Limit).
     */
    fun truncatedText(maxLength: Int = 15000): String {
        return if (text.length <= maxLength) text
        else text.take(maxLength) + "\n\n[... gekürzt, ${text.length - maxLength} weitere Zeichen ...]"
    }
}
