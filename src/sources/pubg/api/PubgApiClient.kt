package sources.`pubg-api`.api

import sources.pubg.model.PlayerStats
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

class PubgApiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.pubg.com"
) {
    private val httpClient = HttpClient.newHttpClient()

    private fun buildRequest(url: String): HttpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/vnd.api+json")
            .GET()
            .build()

    // ─────────────────────────────────────────────────────────────────────────
    // Account-ID anhand des Spielernamens abrufen
    // ─────────────────────────────────────────────────────────────────────────

    fun fetchAccountId(playerName: String, platform: String): String? {
        return try {
            println("🌐 Lade Account-ID für '$playerName' auf $platform...")
            val url = "$baseUrl/shards/$platform/players?filter[playerNames]=$playerName"
            val response = httpClient.send(buildRequest(url), HttpResponse.BodyHandlers.ofString())

            println("🔍 Status: ${response.statusCode()}")
            println("🔍 Body: ${response.body()}")

            if (response.statusCode() != 200) {
                println("❌ HTTP ${response.statusCode()}")
                return null
            }

            val body = response.body()

            // Leeres data-Array → Spieler nicht gefunden
            if (body.trimStart().startsWith("{\"data\":[]}")) {
                println("❌ Keine Daten gefunden für '$playerName'. Prüfe den Namen.")
                return null
            }

            // Erstes "id" nach "data" ist die Account-ID
            val accountId = Regex(""""id"\s*:\s*"([^"]+)"""")
                .find(body.substringAfter("\"data\""))
                ?.groupValues?.get(1)

            if (accountId != null) println("✅ Account-ID: $accountId")
            else println("❌ Account-ID nicht im Response gefunden")
            accountId

        } catch (e: Exception) {
            println("❌ Fehler (Account-ID): ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifetime Wins über alle Game Modes summieren
    // ─────────────────────────────────────────────────────────────────────────

    fun fetchLifetimeWins(platform: String, accountId: String): Int? {
        return try {
            println("🌐 Lade Lifetime Wins für $accountId...")
            val url = "$baseUrl/shards/$platform/players/$accountId/seasons/lifetime"
            val response = httpClient.send(buildRequest(url), HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                println("❌ HTTP ${response.statusCode()}")
                return null
            }

            // Nach "gameModeStats" kommt ein "wins"-Feld pro Game Mode → alle summieren
            val gameModesSection = response.body().substringAfter("\"gameModeStats\"")
            val totalWins = Regex(""""wins"\s*:\s*(\d+)""")
                .findAll(gameModesSection)
                .sumOf { it.groupValues[1].toInt() }

            println("✅ Lifetime Wins: $totalWins")
            totalWins

        } catch (e: Exception) {
            println("❌ Fehler (Lifetime Wins): ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats der letzten N Stunden aus einzelnen Matches sammeln
    // ─────────────────────────────────────────────────────────────────────────

    fun fetchRecentStats(
        platform: String,
        accountId: String,
        hours: Int = 12,
        maxMatches: Int = 30
    ): PlayerStats? {
        return try {
            println("🌐 Lade Spielerdaten für $accountId...")

            // 1. Spieler-Endpoint → Match-ID-Liste
            val playerResponse = httpClient.send(
                buildRequest("$baseUrl/shards/$platform/players/$accountId"),
                HttpResponse.BodyHandlers.ofString()
            )
            if (playerResponse.statusCode() != 200) {
                println("❌ HTTP ${playerResponse.statusCode()} (Spielerdaten)")
                return null
            }

            // Alle "id"-Felder nach "matches" sind Match-UUIDs
            val matchesSection = playerResponse.body().substringAfter("\"matches\"")
            val matchIds = Regex(""""id"\s*:\s*"([^"]+)"""")
                .findAll(matchesSection)
                .map { it.groupValues[1] }
                .take(maxMatches)
                .toList()

            println("📍 ${matchIds.size} Matches gefunden, prüfe letzte $hours Stunden...")

            val cutoff = Instant.now().minusSeconds(hours.toLong() * 3600)
            var wins = 0
            var matches = 0
            var kills = 0
            var deaths = 0
            var damageDealt = 0.0
            var assists = 0

            // Regexe einmal kompilieren, nicht pro Iteration neu erstellen
            val createdAtRegex  = Regex(""""createdAt"\s*:\s*"([^"]+)"""")
            val killsRegex      = Regex(""""kills"\s*:\s*(\d+)""")
            val winPlaceRegex   = Regex(""""winPlace"\s*:\s*(\d+)""")
            val damageRegex     = Regex(""""damageDealt"\s*:\s*([\d.]+)""")
            val assistsRegex    = Regex(""""assists"\s*:\s*(\d+)""")
            val playerIdRegex   = Regex(""""playerId"\s*:\s*"${Regex.escape(accountId)}"""")

            // 2. Einzelne Matches durchgehen
            for (matchId in matchIds) {
                val matchResponse = httpClient.send(
                    buildRequest("$baseUrl/shards/$platform/matches/$matchId"),
                    HttpResponse.BodyHandlers.ofString()
                )
                if (matchResponse.statusCode() != 200) continue

                val matchBody = matchResponse.body()

                // Zeitstempel → außerhalb des Zeitfensters überspringen
                val createdAt = createdAtRegex.find(matchBody)?.groupValues?.get(1) ?: continue
                if (Instant.parse(createdAt) < cutoff) continue

                // 3. Unseren Spieler im "included"-Abschnitt finden
                val includedSection = matchBody.substringAfter("\"included\"")
                val playerMatch = playerIdRegex.find(includedSection) ?: continue

                // kills & winPlace sind im selben stats-Objekt wie playerId
                // → Fenster um unseren Spieler nehmen und dort suchen
                val windowStart = maxOf(0, playerMatch.range.first - 500)
                val windowEnd   = minOf(includedSection.length, playerMatch.range.last + 500)
                val statsWindow = includedSection.substring(windowStart, windowEnd)

                matches++
                kills += killsRegex.find(statsWindow)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                damageDealt += damageRegex.find(statsWindow)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                assists += assistsRegex.find(statsWindow)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val winPlace = winPlaceRegex.find(statsWindow)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (winPlace == 1) wins++ else deaths++
            }

            val result = PlayerStats(wins, matches, kills, deaths, damageDealt, assists)
            println("✅ ${hours}h: ${result.extendedSummary()}")
            result

        } catch (e: Exception) {
            println("❌ Fehler (Recent Stats): ${e.message}")
            null
        }
    }
}