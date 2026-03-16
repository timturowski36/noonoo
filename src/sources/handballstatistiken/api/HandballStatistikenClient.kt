package sources.handballstatistiken.api

import sources.handballstatistiken.model.HandballScorerData
import sources.handballstatistiken.model.HandballScorerStats
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime

/**
 * Lädt die Torjägertabelle von handballstatistiken.de über die JSON-Backend-API.
 *
 * Die Seite ist eine JavaScript-SPA; die eigentlichen Daten liefert:
 *   GET https://handballstatistiken.de/backend/api.php?season=<season>&ligaID=<ligaID>
 *
 * season und ligaID werden aus der Frontend-URL abgeleitet:
 *   https://handballstatistiken.de/<bundesland>/<season>/<ligaID>
 *   z.B. https://handballstatistiken.de/NRW/2526/300268
 *
 * Nutzung:
 *   val client = HandballStatistikenClient()
 *   val data   = client.fetchScorer("https://handballstatistiken.de/NRW/2526/300268")
 */
class HandballStatistikenClient {

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // ──────────────────────────────────────────────────────────────────────────
    // Öffentliche API
    // ──────────────────────────────────────────────────────────────────────────

    fun fetchScorer(url: String): HandballScorerData? {
        println("📊 [HandballStatistiken] Lade Torjägertabelle ...")
        println("   URL: $url")

        val apiUrl = buildApiUrl(url) ?: run {
            println("❌ [HandballStatistiken] URL konnte nicht geparst werden: $url")
            return null
        }

        println("   API: $apiUrl")

        val json = fetchJson(apiUrl) ?: return null
        val players = parseJson(json)

        if (players.isEmpty()) {
            println("❌ [HandballStatistiken] Keine Spieler in der API-Antwort gefunden")
            return null
        }

        println("✅ [HandballStatistiken] ${players.size} Spieler geladen")

        return HandballScorerData(
            url       = url,
            players   = players,
            fetchedAt = LocalDateTime.now()
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // URL-Aufbau
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Wandelt die Frontend-URL in die Backend-API-URL um.
     *
     * Frontend: https://handballstatistiken.de/NRW/2526/300268
     * API:      https://handballstatistiken.de/backend/api.php?season=2526&ligaID=300268
     */
    private fun buildApiUrl(frontendUrl: String): String? {
        // Segmente nach dem Host extrahieren: /NRW/2526/300268
        val path = try { URI.create(frontendUrl).path } catch (e: Exception) { return null }
        val segments = path.trim('/').split('/')
        if (segments.size < 3) return null

        val season = segments[1]
        val ligaId = segments[2]

        if (season.isBlank() || ligaId.isBlank()) return null

        val host = frontendUrl.substringBefore(path)
        return "$host/backend/api.php?season=$season&ligaID=$ligaId"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HTTP
    // ──────────────────────────────────────────────────────────────────────────

    private fun fetchJson(url: String): String? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; FeedKrake/1.0)")
                .header("Accept", "application/json")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() !in 200..299) {
                println("❌ [HandballStatistiken] HTTP ${response.statusCode()}")
                return null
            }

            response.body()
        } catch (e: Exception) {
            println("❌ [HandballStatistiken] HTTP-Fehler: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // JSON-Parsing (ohne externe Library)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parst das JSON-Array unter dem Schlüssel "spieler" und sortiert nach Toren.
     * Der Rang wird aus der sortierten Reihenfolge berechnet.
     */
    private fun parseJson(json: String): List<HandballScorerStats> {
        val objects = extractJsonObjects(json, "spieler") ?: return emptyList()

        return objects
            .mapIndexedNotNull { idx, obj -> parsePlayer(idx + 1, obj) }
            // API liefert bereits sortiert, aber zur Sicherheit nochmals
            .sortedByDescending { it.tore }
            .mapIndexed { idx, p -> p.copy(rang = idx + 1) }
    }

    private fun parsePlayer(defaultRang: Int, json: String): HandballScorerStats? {
        return try {
            val siebGew = extractInt(json, "Siebenmeter_geworfen") ?: 0
            val siebTore = extractInt(json, "Siebenmeter_Tore") ?: 0
            val pct = if (siebGew > 0) "%.1f%%".format(siebTore * 100.0 / siebGew) else "-"

            HandballScorerStats(
                rang                = defaultRang,
                name                = extractString(json, "Name") ?: return null,
                mannschaft          = extractString(json, "Mannschaft") ?: return null,
                trNr                = extractInt(json, "Trikot_Nr")?.toString() ?: "-",
                spiele              = extractIntOrDouble(json, "Spiele") ?: 0,
                tore                = extractIntOrDouble(json, "Tore") ?: 0,
                feldtore            = extractIntOrDouble(json, "Feldtore") ?: 0,
                siebenmeterTore     = extractInt(json, "Siebenmeter_Tore") ?: 0,
                siebenmeterGeworfen = siebGew,
                siebenmeterProzent  = pct,
                letztesSpiel        = extractString(json, "toreLS") ?: "-",
                toreProSpiel        = extractDouble(json, "Durchschnitt") ?: 0.0,
                feldtoreProSpiel    = extractDouble(json, "FeldtoreProSpiel") ?: 0.0,
                verwarnungen        = extractInt(json, "Verwarnungen") ?: 0,
                zweiMinuten         = extractInt(json, "Zeitstrafen") ?: 0,
                disqualifikationen  = extractInt(json, "Disqualifikationen") ?: 0
            )
        } catch (e: Exception) {
            println("⚠️  [HandballStatistiken] Spieler konnte nicht geparst werden: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // JSON-Hilfsfunktionen (ähnlich wie HandballScraper im Projekt)
    // ──────────────────────────────────────────────────────────────────────────

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.replace("\\u00f6", "ö")
            ?.replace("\\u00e4", "ä")
            ?.replace("\\u00fc", "ü")
            ?.replace("\\u00d6", "Ö")
            ?.replace("\\u00c4", "Ä")
            ?.replace("\\u00dc", "Ü")
            ?.replace("\\u00df", "ß")
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = Regex(""""$key"\s*:\s*(\d+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractDouble(json: String, key: String): Double? {
        val pattern = Regex(""""$key"\s*:\s*([\d.]+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /** Liest Integer-Felder die in der API manchmal als Float geliefert werden (z.B. "Tore":58.0). */
    private fun extractIntOrDouble(json: String, key: String): Int? {
        val pattern = Regex(""""$key"\s*:\s*([\d.]+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
    }

    /**
     * Extrahiert alle JSON-Objekte aus einem Array-Feld.
     * Funktioniert für einfache Fälle ohne verschachtelte Arrays.
     */
    private fun extractJsonObjects(json: String, key: String): List<String>? {
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
