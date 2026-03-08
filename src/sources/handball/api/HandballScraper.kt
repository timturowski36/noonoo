package sources.handball.api

import sources.handball.model.HandballMatch
import sources.handball.model.HandballScheduleData
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Client für handball.net / handball4all Spielpläne.
 *
 * Versucht mehrere Strategien:
 * 1. Direkte handball4all API (JSON)
 * 2. HTML-Scraping von handball.net
 */
class HandballScraper {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    companion object {
        // handball4all verwendet diese Struktur für Team-IDs:
        // handball4all.westfalen.1309001 -> Verband: westfalen, Team-Nr: 1309001
        private val TEAM_ID_PATTERN = Regex("""handball4all\.(\w+)\.(\d+)""")
    }

    /**
     * Lädt den Spielplan für ein Team.
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
        println("🤾 [Handball] Lade Spielplan für $teamId...")

        // Versuche zuerst die API
        val apiResult = tryFetchFromApi(teamId)
        if (apiResult != null) {
            return apiResult
        }

        // Fallback: HTML scraping
        println("   ⚠️ API nicht erreichbar, versuche HTML-Scraping...")
        return tryFetchFromHtml(teamId, seasonFrom, seasonTo)
    }

    /**
     * Versucht Daten von der handball4all API zu laden.
     */
    private fun tryFetchFromApi(teamId: String): HandballScheduleData? {
        val match = TEAM_ID_PATTERN.find(teamId) ?: return null
        val verband = match.groupValues[1]
        val teamNr = match.groupValues[2]

        // handball4all API Endpunkte (öffentlich zugänglich)
        val apiUrls = listOf(
            "https://api.h4a.mobi/h4a/v0/$verband/teams/$teamNr/games",
            "https://spo.handball4all.de/service/if_g_json.php?cmd=pcu&sp0=$teamNr"
        )

        for (url in apiUrls) {
            try {
                println("   🌐 Versuche: $url")
                val response = fetchWithTimeout(url, 8)

                if (response != null && response.contains("\"")) {
                    val matches = parseApiResponse(response, teamId)
                    if (matches.isNotEmpty()) {
                        println("   ✅ ${matches.size} Spiele von API geladen")
                        return HandballScheduleData(
                            teamId = teamId,
                            teamName = extractTeamNameFromApi(response) ?: "HSG RE/OE",
                            season = "2025/26",
                            matches = matches,
                            fetchedAt = LocalDateTime.now()
                        )
                    }
                }
            } catch (e: Exception) {
                println("   ⚠️ API-Fehler: ${e.message}")
            }
        }

        return null
    }

    /**
     * Fallback: HTML-Scraping von handball.net
     */
    private fun tryFetchFromHtml(
        teamId: String,
        seasonFrom: String,
        seasonTo: String
    ): HandballScheduleData? {
        val url = "https://www.handball.net/mannschaften/$teamId/spielplan?dateFrom=$seasonFrom&dateTo=$seasonTo"
        println("   🌐 Lade HTML: $url")

        return try {
            val html = fetchWithTimeout(url, 15) ?: return null
            val matches = parseHtmlMatches(html)
            val teamName = extractTeamNameFromHtml(html) ?: "HSG RE/OE"

            if (matches.isEmpty()) {
                println("   ⚠️ Keine Spiele im HTML gefunden")
                // Erstelle Demo-Daten für Tests
                return createDemoData(teamId)
            }

            println("   ✅ ${matches.size} Spiele aus HTML extrahiert")

            HandballScheduleData(
                teamId = teamId,
                teamName = teamName,
                season = "2025/26",
                matches = matches,
                fetchedAt = LocalDateTime.now()
            )
        } catch (e: Exception) {
            println("   ❌ HTML-Fehler: ${e.message}")
            // Bei Fehler: Demo-Daten für Tests
            createDemoData(teamId)
        }
    }

    private fun fetchWithTimeout(url: String, timeoutSeconds: Int): String? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/json,*/*")
            .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return if (response.statusCode() in 200..299) {
            response.body()
        } else {
            println("   ❌ HTTP ${response.statusCode()}")
            null
        }
    }

    private fun parseApiResponse(json: String, teamId: String): List<HandballMatch> {
        val matches = mutableListOf<HandballMatch>()

        // Suche nach Spiel-Objekten im JSON
        // Typische Felder: gDate, gTime, gHomeTeam, gGuestTeam, gHomeGoals, gGuestGoals
        val gamePattern = Regex("""\{[^{}]*"g(?:Date|Home|Guest|Goals)[^{}]*}""", RegexOption.DOT_MATCHES_ALL)

        gamePattern.findAll(json).forEachIndexed { index, match ->
            parseGameFromJson(match.value, index)?.let { matches.add(it) }
        }

        return matches
    }

    private fun parseGameFromJson(json: String, index: Int): HandballMatch? {
        fun extract(key: String): String? {
            val pattern = Regex(""""$key"\s*:\s*"([^"\\]*)"""")
            return pattern.find(json)?.groupValues?.get(1)
        }

        fun extractInt(key: String): Int? {
            val pattern = Regex(""""$key"\s*:\s*(\d+)""")
            return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
        }

        val dateStr = extract("gDate") ?: extract("date") ?: return null
        val timeStr = extract("gTime") ?: extract("time") ?: "15:00"
        val homeTeam = extract("gHomeTeam") ?: extract("homeTeam") ?: return null
        val awayTeam = extract("gGuestTeam") ?: extract("guestTeam") ?: extract("awayTeam") ?: return null
        val venue = extract("gGymnasiumName") ?: extract("venue")
        val scoreHome = extractInt("gHomeGoals") ?: extractInt("homeGoals")
        val scoreAway = extractInt("gGuestGoals") ?: extractInt("guestGoals")

        val date = parseDateTime(dateStr, timeStr) ?: return null

        return HandballMatch(
            id = "game_$index",
            date = date,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            venue = venue,
            scoreHome = scoreHome,
            scoreAway = scoreAway,
            isPlayed = scoreHome != null && scoreAway != null
        )
    }

    private fun parseHtmlMatches(html: String): List<HandballMatch> {
        val matches = mutableListOf<HandballMatch>()

        // Suche nach Spiel-Einträgen mit Datum-Pattern
        val datePattern = Regex("""(\d{1,2})\.(\d{2})\.""")
        val timePattern = Regex("""(\d{1,2}):(\d{2})""")
        val teamPattern = Regex("""(HSG|FC|TV|TuS|VfL|SG|SC|TSV|SpVg|HC|HG|TG|MTV|SVG|JSG)[^\n<]{2,30}""", RegexOption.IGNORE_CASE)

        // Vereinfachtes Parsing: Suche Zeilen mit Datum und Teams
        val lines = html.split(Regex("""</?(?:div|tr|li)[^>]*>"""))

        var index = 0
        for (line in lines) {
            val dateMatch = datePattern.find(line) ?: continue
            val teams = teamPattern.findAll(line).map { it.value.trim() }.toList()
            if (teams.size < 2) continue

            val day = dateMatch.groupValues[1].toIntOrNull() ?: continue
            val month = dateMatch.groupValues[2].toIntOrNull() ?: continue
            val year = if (month >= 7) 2025 else 2026

            val timeMatch = timePattern.find(line)
            val hour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 15
            val minute = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

            val date = try {
                LocalDateTime.of(year, month, day, hour, minute)
            } catch (e: Exception) {
                continue
            }

            val scorePattern = Regex("""(\d+)\s*:\s*(\d+)""")
            val scoreMatch = scorePattern.find(line)
            val scoreHome = scoreMatch?.groupValues?.get(1)?.toIntOrNull()
            val scoreAway = scoreMatch?.groupValues?.get(2)?.toIntOrNull()

            matches.add(
                HandballMatch(
                    id = "match_$index",
                    date = date,
                    homeTeam = teams[0],
                    awayTeam = teams[1],
                    venue = null,
                    scoreHome = scoreHome,
                    scoreAway = scoreAway,
                    isPlayed = scoreHome != null && scoreAway != null && (scoreHome > 0 || scoreAway > 0)
                )
            )
            index++
        }

        return matches
    }

    private fun extractTeamNameFromApi(json: String): String? {
        val pattern = Regex(""""(?:teamName|gHomeTeam|gGuestTeam)"\s*:\s*"([^"]*HSG[^"]*)"""", RegexOption.IGNORE_CASE)
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractTeamNameFromHtml(html: String): String? {
        val titlePattern = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
        val titleMatch = titlePattern.find(html)
        if (titleMatch != null) {
            val title = titleMatch.groupValues[1]
            val teamPattern = Regex("""(HSG[^|<-]+|FC[^|<-]+|TV[^|<-]+)""", RegexOption.IGNORE_CASE)
            teamPattern.find(title)?.let { return it.value.trim() }
        }
        return null
    }

    private fun parseDateTime(dateStr: String, timeStr: String): LocalDateTime? {
        return try {
            // Format: "13.09.2025" oder "2025-09-13"
            val dateParts = if (dateStr.contains(".")) {
                dateStr.split(".").map { it.toInt() }
            } else {
                dateStr.split("-").map { it.toInt() }.reversed()
            }

            val timeParts = timeStr.split(":").map { it.toInt() }

            if (dateParts.size >= 3 && timeParts.size >= 2) {
                val day = dateParts[0]
                val month = dateParts[1]
                val year = if (dateParts[2] < 100) 2000 + dateParts[2] else dateParts[2]
                LocalDateTime.of(year, month, day, timeParts[0], timeParts[1])
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Erstellt Demo-Daten wenn keine echten Daten verfügbar sind.
     * Nützlich für Tests und Entwicklung.
     */
    private fun createDemoData(teamId: String): HandballScheduleData {
        println("   📋 Erstelle Demo-Spielplan für Tests...")

        val now = LocalDateTime.now()
        val matches = listOf(
            HandballMatch("demo_1", now.minusDays(14), "HSG RE/OE", "FC Schalke 04", "Willi-Winter-Halle", 27, 25, true),
            HandballMatch("demo_2", now.minusDays(7), "HSV Herbede", "HSG RE/OE", "Sporthalle Herbede", 20, 27, true),
            HandballMatch("demo_3", now.plusDays(7), "HSG RE/OE", "TuS Bommern", "Willi-Winter-Halle", null, null, false),
            HandballMatch("demo_4", now.plusDays(14), "SG Langendreer", "HSG RE/OE", "Sporthalle Langendreer", null, null, false),
            HandballMatch("demo_5", now.plusDays(21), "HSG RE/OE", "TV Wattenscheid", "Willi-Winter-Halle", null, null, false)
        )

        return HandballScheduleData(
            teamId = teamId,
            teamName = "HSG RE/OE (Demo)",
            season = "2025/26",
            matches = matches,
            fetchedAt = now
        )
    }
}
