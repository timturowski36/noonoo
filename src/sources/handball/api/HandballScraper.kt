package sources.handball.api

import config.EnvConfig
import sources.claude.api.ClaudeApiClient
import sources.claude.prompts.PromptContext
import sources.handball.model.HandballMatch
import sources.handball.model.HandballScheduleData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Handball-Client der Claude verwendet um Spielpläne von handball.net zu extrahieren.
 */
class HandballScraper {

    private val claudeClient: ClaudeApiClient? = run {
        val apiKey = EnvConfig.claudeApiKey()
        if (apiKey != null) ClaudeApiClient(apiKey) else null
    }

    companion object {
        private val HANDBALL_SCHEDULE_CONTEXT = PromptContext(
            name = "handball_full_schedule",
            description = "Vollständiger Handball-Spielplan (vergangene + kommende Spiele)",
            systemPrompt = """Du bist ein Handball-Daten-Assistent. Extrahiere ALLE Spiele aus dem Spielplan.

WICHTIG: Die Seite zeigt ZWEI Arten von Spielen:
1. GESPIELTE Spiele: zeigen ein Ergebnis wie "25:23" oder "30:26"
2. KOMMENDE Spiele: zeigen eine Uhrzeit wie "15:00 Uhr" oder "18:00 Uhr" OHNE Ergebnis

Du MUSST beide Arten extrahieren! Kommende Spiele erkennst du daran, dass statt "25:23" etwas wie "15:00 Uhr" steht.

Antworte NUR mit validem JSON, keine Erklärungen:
{
  "team": "Mannschaftsname",
  "league": "Liga/Klasse",
  "season": "2025/26",
  "matches": [
    {
      "date": "2025-09-13",
      "time": "18:30",
      "homeTeam": "Heimteam",
      "awayTeam": "Gastteam",
      "venue": "Hallenname",
      "scoreHome": 25,
      "scoreAway": 23,
      "isPlayed": true
    },
    {
      "date": "2026-03-15",
      "time": "15:00",
      "homeTeam": "Heimteam",
      "awayTeam": "Gastteam",
      "venue": "Hallenname",
      "scoreHome": null,
      "scoreAway": null,
      "isPlayed": false
    }
  ]
}

REGELN:
- Extrahiere ALLE Spiele (gespielt UND kommend)
- Gespielt: scoreHome/scoreAway = Zahlen, isPlayed = true
- Kommend (zeigt "XX:XX Uhr"): scoreHome/scoreAway = null, isPlayed = false, time = die angezeigte Uhrzeit
- Datum im Format YYYY-MM-DD, Zeit im Format HH:MM
- Sortiere nach Datum aufsteigend""",
            userPrefix = "",
            userSuffix = ""
        )

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    /**
     * Lädt den Spielplan für ein Team via Claude.
     */
    fun fetchSchedule(
        teamId: String,
        seasonFrom: String = "2025-07-01",
        seasonTo: String = "2026-06-30"
    ): HandballScheduleData? {
        if (claudeClient == null) {
            println("❌ [Handball] Claude API Key nicht konfiguriert!")
            println("   Füge CLAUDE_API_KEY in .env hinzu")
            return createDemoData(teamId)
        }

        val url = "https://www.handball.net/mannschaften/$teamId/spielplan?dateFrom=$seasonFrom&dateTo=$seasonTo"

        println("🤾 [Handball] Lade Spielplan via Claude...")
        println("   URL: $url")

        val response = claudeClient.extractFromWebpage(
            url = url,
            context = HANDBALL_SCHEDULE_CONTEXT,
            additionalPrompt = "Extrahiere alle Spiele aus dem Spielplan. Das gesuchte Team ist wahrscheinlich HSG RE/OE oder ähnlich."
        )

        if (response == null) {
            println("❌ [Handball] Claude konnte die Seite nicht analysieren")
            return createDemoData(teamId)
        }

        println("📝 [Handball] Claude Antwort erhalten, parse JSON...")

        return parseClaudeResponse(response.text, teamId) ?: run {
            println("⚠️ [Handball] JSON-Parsing fehlgeschlagen, verwende Demo-Daten")
            createDemoData(teamId)
        }
    }

    private fun parseClaudeResponse(json: String, teamId: String): HandballScheduleData? {
        try {
            // Extrahiere Team-Info
            val team = extractString(json, "team") ?: "HSG RE/OE"
            val league = extractString(json, "league") ?: "Kreisliga"
            val season = extractString(json, "season") ?: "2025/26"

            // Extrahiere Matches-Array
            val matchesArray = extractArray(json, "matches") ?: return null
            val matches = matchesArray.mapIndexedNotNull { index, matchJson ->
                parseMatch(matchJson, index)
            }

            if (matches.isEmpty()) {
                println("⚠️ [Handball] Keine Spiele im JSON gefunden")
                return null
            }

            println("✅ [Handball] ${matches.size} Spiele extrahiert")

            return HandballScheduleData(
                teamId = teamId,
                teamName = team,
                season = season,
                matches = matches,
                fetchedAt = LocalDateTime.now()
            )
        } catch (e: Exception) {
            println("❌ [Handball] Parse-Fehler: ${e.message}")
            return null
        }
    }

    private fun parseMatch(json: String, index: Int): HandballMatch? {
        val dateStr = extractString(json, "date") ?: return null
        val timeStr = extractString(json, "time") ?: "15:00"
        val homeTeam = extractString(json, "homeTeam") ?: return null
        val awayTeam = extractString(json, "awayTeam") ?: return null
        val venue = extractString(json, "venue")
        val scoreHome = extractInt(json, "scoreHome")
        val scoreAway = extractInt(json, "scoreAway")

        val date = parseDateTime(dateStr, timeStr) ?: return null

        // Robuste isPlayed-Ermittlung: Datum in Zukunft = IMMER nicht gespielt
        val actuallyPlayed = if (date.isAfter(LocalDateTime.now())) {
            false  // Zukunft = definitiv nicht gespielt
        } else {
            // Vergangenheit: gespielt wenn Score vorhanden
            scoreHome != null && scoreAway != null
        }

        return HandballMatch(
            id = "match_$index",
            date = date,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            venue = venue,
            scoreHome = scoreHome,
            scoreAway = scoreAway,
            isPlayed = actuallyPlayed
        )
    }

    private fun parseDateTime(dateStr: String, timeStr: String): LocalDateTime? {
        return try {
            val timeParts = timeStr.split(":").map { it.toInt() }
            val date = java.time.LocalDate.parse(dateStr, DATE_FORMATTER)
            LocalDateTime.of(date, java.time.LocalTime.of(timeParts[0], timeParts.getOrElse(1) { 0 }))
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // JSON-Parsing Hilfsfunktionen (ohne Library)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = Regex(""""$key"\s*:\s*(\d+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val pattern = Regex(""""$key"\s*:\s*(true|false)""")
        return pattern.find(json)?.groupValues?.get(1)?.toBoolean()
    }

    private fun extractArray(json: String, key: String): List<String>? {
        // Finde den Array-Start
        val keyPattern = """"$key"\s*:\s*\["""
        val startMatch = Regex(keyPattern).find(json) ?: return null
        val arrayStart = startMatch.range.last + 1

        // Finde alle Objekte im Array
        val objects = mutableListOf<String>()
        var depth = 0
        var objectStart = -1

        for (i in arrayStart until json.length) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        objects.add(json.substring(objectStart, i + 1))
                        objectStart = -1
                    }
                }
                ']' -> if (depth == 0) break
            }
        }

        return objects.ifEmpty { null }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Demo-Daten für Tests
    // ─────────────────────────────────────────────────────────────────────────────

    private fun createDemoData(teamId: String): HandballScheduleData {
        println("📋 [Handball] Erstelle Demo-Spielplan für Tests...")

        val now = LocalDateTime.now()
        val matches = listOf(
            HandballMatch("demo_1", now.minusDays(14).withHour(18).withMinute(30), "HSG RE/OE", "FC Schalke 04", "Willi-Winter-Halle", 27, 25, true),
            HandballMatch("demo_2", now.minusDays(7).withHour(17).withMinute(0), "HSV Herbede", "HSG RE/OE", "Sporthalle Herbede", 20, 27, true),
            HandballMatch("demo_3", now.plusDays(7).withHour(18).withMinute(30), "HSG RE/OE", "TuS Bommern", "Willi-Winter-Halle", null, null, false),
            HandballMatch("demo_4", now.plusDays(14).withHour(17).withMinute(0), "SG Langendreer", "HSG RE/OE", "Sporthalle Langendreer", null, null, false),
            HandballMatch("demo_5", now.plusDays(21).withHour(18).withMinute(30), "HSG RE/OE", "TV Wattenscheid", "Willi-Winter-Halle", null, null, false)
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
