package sources.handball.api

import sources.handball.model.HandballMatch
import sources.handball.model.HandballScheduleData
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Scraper für handball.net Spielpläne.
 *
 * Da die Seite React-basiert ist, wird versucht:
 * 1. Embedded JSON-Daten zu extrahieren
 * 2. HTML-Struktur zu parsen
 */
class HandballScraper {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val germanDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN)
    private val shortDatePattern = Regex("""(\w{2}),?\s*(\d{1,2})\.(\d{2})\.?""")
    private val timePattern = Regex("""(\d{1,2}):(\d{2})""")
    private val scorePattern = Regex("""(\d+)\s*:\s*(\d+)""")

    /**
     * Lädt den Spielplan von handball.net.
     *
     * @param teamId Die Team-ID (z.B. "handball4all.westfalen.1309001")
     * @param seasonFrom Start der Saison (z.B. "2025-07-01")
     * @param seasonTo Ende der Saison (z.B. "2026-06-30")
     */
    fun fetchSchedule(
        teamId: String,
        seasonFrom: String = "2025-07-01",
        seasonTo: String = "2026-06-30"
    ): HandballScheduleData? {
        val url = "https://www.handball.net/mannschaften/$teamId/spielplan?dateFrom=$seasonFrom&dateTo=$seasonTo"

        println("🤾 [Handball] Lade Spielplan...")
        println("   URL: $url")

        return try {
            val html = fetchHtml(url) ?: return null
            val matches = parseMatches(html, teamId)
            val teamName = extractTeamName(html) ?: "HSG RE/OE"

            if (matches.isEmpty()) {
                println("⚠️ [Handball] Keine Spiele gefunden - Seite möglicherweise dynamisch geladen")
                return null
            }

            println("✅ [Handball] ${matches.size} Spiele gefunden")

            HandballScheduleData(
                teamId = teamId,
                teamName = teamName,
                season = "2025/26",
                matches = matches,
                fetchedAt = LocalDateTime.now()
            )
        } catch (e: Exception) {
            println("❌ [Handball] Fehler: ${e.message}")
            null
        }
    }

    private fun fetchHtml(url: String): String? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return if (response.statusCode() in 200..299) {
            response.body()
        } else {
            println("❌ [Handball] HTTP ${response.statusCode()}")
            null
        }
    }

    /**
     * Extrahiert Spiele aus dem HTML.
     * Versucht verschiedene Parsing-Strategien.
     */
    private fun parseMatches(html: String, teamId: String): List<HandballMatch> {
        val matches = mutableListOf<HandballMatch>()

        // Strategie 1: Suche nach eingebettetem JSON (__NEXT_DATA__ oder ähnlich)
        val jsonMatches = tryParseEmbeddedJson(html)
        if (jsonMatches.isNotEmpty()) {
            return jsonMatches
        }

        // Strategie 2: Parse aus HTML-Struktur
        // Handball.net nutzt div-basierte Layouts für Spiele
        val gameBlocks = extractGameBlocks(html)

        gameBlocks.forEachIndexed { index, block ->
            parseGameBlock(block, index)?.let { matches.add(it) }
        }

        return matches
    }

    private fun tryParseEmbeddedJson(html: String): List<HandballMatch> {
        // Suche nach __NEXT_DATA__ (Next.js) oder ähnlichen embedded JSON
        val nextDataPattern = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val match = nextDataPattern.find(html)

        if (match != null) {
            println("📦 [Handball] Next.js Daten gefunden, parse...")
            return parseNextDataJson(match.groupValues[1])
        }

        return emptyList()
    }

    private fun parseNextDataJson(json: String): List<HandballMatch> {
        val matches = mutableListOf<HandballMatch>()

        // Einfaches Regex-basiertes Parsing für Spieleinträge
        // Suche nach Mustern wie "date":"...", "homeTeam":"...", etc.
        val gamePattern = Regex(""""games?"\s*:\s*\[([\s\S]*?)]""")
        val gamesMatch = gamePattern.find(json)

        if (gamesMatch != null) {
            val gamesJson = gamesMatch.groupValues[1]

            // Extrahiere einzelne Game-Objekte
            val gameObjects = Regex("""\{[^{}]*(?:\{[^{}]*}[^{}]*)*}""").findAll(gamesJson)

            gameObjects.forEachIndexed { index, gameMatch ->
                parseGameJson(gameMatch.value, index)?.let { matches.add(it) }
            }
        }

        return matches
    }

    private fun parseGameJson(json: String, index: Int): HandballMatch? {
        // Extrahiere Felder aus JSON-Objekt
        fun extractField(key: String): String? {
            val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
            return pattern.find(json)?.groupValues?.get(1)
        }

        fun extractInt(key: String): Int? {
            val pattern = Regex(""""$key"\s*:\s*(\d+)""")
            return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
        }

        val dateStr = extractField("date") ?: extractField("gameDate") ?: return null
        val homeTeam = extractField("homeTeam") ?: extractField("home") ?: return null
        val awayTeam = extractField("awayTeam") ?: extractField("away") ?: return null
        val venue = extractField("venue") ?: extractField("location")
        val scoreHome = extractInt("scoreHome") ?: extractInt("goalsHome")
        val scoreAway = extractInt("scoreAway") ?: extractInt("goalsAway")

        val date = parseDate(dateStr) ?: return null
        val isPlayed = scoreHome != null && scoreAway != null

        return HandballMatch(
            id = "game_$index",
            date = date,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            venue = venue,
            scoreHome = scoreHome,
            scoreAway = scoreAway,
            isPlayed = isPlayed
        )
    }

    private fun extractGameBlocks(html: String): List<String> {
        // Versuche Spielblöcke zu identifizieren
        // Typische Struktur: <div>...<team>...<score>...<team>...</div>
        val blocks = mutableListOf<String>()

        // Pattern für Spielzeilen mit Datum + Teams + Score
        val rowPattern = Regex(
            """<div[^>]*>.*?(\d{1,2}\.\d{2}\.).*?(HSG|FC|TV|TuS|VfL|SG|SC|TSV|SpVg).*?</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        rowPattern.findAll(html).forEach { blocks.add(it.value) }

        return blocks
    }

    private fun parseGameBlock(block: String, index: Int): HandballMatch? {
        // Extrahiere Datum
        val dateMatch = shortDatePattern.find(block) ?: return null
        val day = dateMatch.groupValues[2].toIntOrNull() ?: return null
        val month = dateMatch.groupValues[3].toIntOrNull() ?: return null

        // Extrahiere Zeit
        val timeMatch = timePattern.find(block)
        val hour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 15
        val minute = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

        // Bestimme Jahr basierend auf Monat (Saison geht von Juli bis Juni)
        val year = if (month >= 7) 2025 else 2026

        val date = try {
            LocalDateTime.of(year, month, day, hour, minute)
        } catch (e: Exception) {
            return null
        }

        // Extrahiere Teams (vereinfachte Heuristik)
        val teamPattern = Regex("""(HSG[^<,]+|FC[^<,]+|TV[^<,]+|TuS[^<,]+|VfL[^<,]+|SG[^<,]+|SC[^<,]+|TSV[^<,]+|SpVg[^<,]+)""", RegexOption.IGNORE_CASE)
        val teams = teamPattern.findAll(block).map { it.value.trim() }.toList()

        if (teams.size < 2) return null

        val homeTeam = teams[0]
        val awayTeam = teams[1]

        // Extrahiere Score
        val scoreMatch = scorePattern.find(block)
        val scoreHome = scoreMatch?.groupValues?.get(1)?.toIntOrNull()
        val scoreAway = scoreMatch?.groupValues?.get(2)?.toIntOrNull()
        val isPlayed = scoreHome != null && scoreAway != null && (scoreHome > 0 || scoreAway > 0)

        return HandballMatch(
            id = "match_$index",
            date = date,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            venue = null,
            scoreHome = scoreHome,
            scoreAway = scoreAway,
            isPlayed = isPlayed
        )
    }

    private fun extractTeamName(html: String): String? {
        // Suche nach Team-Namen im Titel oder Header
        val titlePattern = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
        val titleMatch = titlePattern.find(html)

        if (titleMatch != null) {
            val title = titleMatch.groupValues[1]
            // Extrahiere HSG/TuS/etc. aus Titel
            val teamPattern = Regex("""(HSG[^|<-]+|FC[^|<-]+|TV[^|<-]+)""", RegexOption.IGNORE_CASE)
            teamPattern.find(title)?.let { return it.value.trim() }
        }

        return null
    }

    private fun parseDate(dateStr: String): LocalDateTime? {
        return try {
            // Versuche verschiedene Formate
            val cleanDate = dateStr.trim()

            // ISO Format: 2025-09-13T18:30:00
            if (cleanDate.contains("T")) {
                return LocalDateTime.parse(cleanDate.substringBefore("+").substringBefore("Z"))
            }

            // Deutsches Format: 13.09.2025 18:30
            if (cleanDate.matches(Regex("""\d{2}\.\d{2}\.\d{4}\s+\d{2}:\d{2}"""))) {
                return LocalDateTime.parse(cleanDate, germanDateFormatter)
            }

            null
        } catch (e: Exception) {
            null
        }
    }
}
