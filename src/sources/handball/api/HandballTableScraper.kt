package sources.handball.api

import config.EnvConfig
import sources.claude.api.ClaudeApiClient
import sources.claude.prompts.PromptContext
import sources.handball.model.HandballTableData
import sources.handball.model.HandballTableEntry
import java.time.LocalDateTime

/**
 * Scraper für Handball-Tabellen von handball.net.
 */
class HandballTableScraper {

    private val claudeClient: ClaudeApiClient? = run {
        val apiKey = EnvConfig.claudeApiKey()
        if (apiKey != null) ClaudeApiClient(apiKey) else null
    }

    companion object {
        private val TABLE_CONTEXT = PromptContext(
            name = "handball_table",
            description = "Handball-Tabelle extrahieren",
            systemPrompt = """Du bist ein Handball-Daten-Assistent. Extrahiere die Tabelle aus dem Spielplan.

Antworte NUR mit validem JSON, keine Erklärungen:
{
  "league": "Männer Bezirksliga Ruhrgebiet",
  "season": "2025/26",
  "table": [
    {
      "position": 1,
      "teamName": "Teamname",
      "games": 15,
      "wins": 10,
      "draws": 2,
      "losses": 3,
      "goalsFor": 450,
      "goalsAgainst": 380,
      "goalDiff": 70,
      "points": 22
    }
  ]
}

WICHTIG:
- Extrahiere ALLE Teams aus der Tabelle
- position: Tabellenplatz (1-N)
- games: Anzahl Spiele
- wins/draws/losses: Siege/Unentschieden/Niederlagen
- goalsFor/goalsAgainst: Erzielte/Kassierte Tore
- goalDiff: Tordifferenz (goalsFor - goalsAgainst)
- points: Punkte""",
            userPrefix = "",
            userSuffix = ""
        )
    }

    /**
     * Lädt die Tabelle von handball.net.
     */
    fun fetchTable(teamId: String): HandballTableData? {
        if (claudeClient == null) {
            println("❌ [Tabelle] Claude API Key nicht konfiguriert!")
            return null
        }

        val url = "https://www.handball.net/mannschaften/$teamId/tabelle"

        println("📊 [Tabelle] Lade Tabelle via Claude...")
        println("   URL: $url")

        val response = claudeClient.extractFromWebpage(
            url = url,
            context = TABLE_CONTEXT,
            additionalPrompt = "Extrahiere die komplette Tabelle mit allen Teams."
        )

        if (response == null) {
            println("❌ [Tabelle] Claude konnte die Seite nicht analysieren")
            return null
        }

        println("📝 [Tabelle] Claude Antwort erhalten, parse JSON...")

        return parseClaudeResponse(response.text, teamId)
    }

    private fun parseClaudeResponse(json: String, teamId: String): HandballTableData? {
        try {
            val league = extractString(json, "league") ?: "Unbekannte Liga"
            val season = extractString(json, "season") ?: "2025/26"

            val tableArray = extractArray(json, "table") ?: return null
            val entries = tableArray.mapNotNull { entryJson ->
                parseTableEntry(entryJson)
            }

            if (entries.isEmpty()) {
                println("⚠️ [Tabelle] Keine Einträge gefunden")
                return null
            }

            println("✅ [Tabelle] ${entries.size} Teams extrahiert")

            return HandballTableData(
                teamId = teamId,
                league = league,
                season = season,
                entries = entries.sortedBy { it.position },
                fetchedAt = LocalDateTime.now()
            )
        } catch (e: Exception) {
            println("❌ [Tabelle] Parse-Fehler: ${e.message}")
            return null
        }
    }

    private fun parseTableEntry(json: String): HandballTableEntry? {
        val position = extractInt(json, "position") ?: return null
        val teamName = extractString(json, "teamName") ?: return null
        val games = extractInt(json, "games") ?: 0
        val wins = extractInt(json, "wins") ?: 0
        val draws = extractInt(json, "draws") ?: 0
        val losses = extractInt(json, "losses") ?: 0
        val goalsFor = extractInt(json, "goalsFor") ?: 0
        val goalsAgainst = extractInt(json, "goalsAgainst") ?: 0
        val goalDiff = extractInt(json, "goalDiff") ?: (goalsFor - goalsAgainst)
        val points = extractInt(json, "points") ?: 0

        return HandballTableEntry(
            position = position,
            teamName = teamName,
            games = games,
            wins = wins,
            draws = draws,
            losses = losses,
            goalsFor = goalsFor,
            goalsAgainst = goalsAgainst,
            goalDiff = goalDiff,
            points = points
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // JSON-Parsing Hilfsfunktionen
    // ─────────────────────────────────────────────────────────────────────────────

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = Regex(""""$key"\s*:\s*(-?\d+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractArray(json: String, key: String): List<String>? {
        val keyPattern = """"$key"\s*:\s*\["""
        val startMatch = Regex(keyPattern).find(json) ?: return null
        val arrayStart = startMatch.range.last + 1

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
}
