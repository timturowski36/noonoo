package sources.`pubg-api`.api

import sources.pubg.model.PlayerStats
import java.io.File
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

    // Millisekunden zwischen Match-Requests (6s = max 10 Requests/Min)
    private val requestDelayMs = 6_000L

    private fun buildRequest(url: String): HttpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/vnd.api+json")
            .GET()
            .build()

    private fun rateLimitedSend(url: String): HttpResponse<String> {
        Thread.sleep(requestDelayMs)
        return httpClient.send(buildRequest(url), HttpResponse.BodyHandlers.ofString())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Account-ID anhand des Spielernamens abrufen (mit File-Cache)
    // ─────────────────────────────────────────────────────────────────────────

    fun fetchAccountId(playerName: String, platform: String): String? {
        // Cache-Datei prüfen
        val cacheFile = File("src/sources/pubg/config/${playerName}_accountid.txt")
        if (cacheFile.exists()) {
            val cached = cacheFile.readText().trim()
            if (cached.isNotEmpty()) {
                println("✅ Account-ID aus Cache: $cached")
                return cached
            }
        }

        return try {
            println("🌐 Lade Account-ID für '$playerName' auf $platform...")
            val url = "$baseUrl/shards/$platform/players?filter[playerNames]=$playerName"
            val response = httpClient.send(buildRequest(url), HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                println("❌ HTTP ${response.statusCode()}")
                return null
            }

            val body = response.body()

            // Leeres data-Array am Anfang = Spieler nicht gefunden
            if (body.trimStart().startsWith("{\"data\":[]}")) {
                println("❌ Spieler '$playerName' nicht gefunden.")
                return null
            }

            // Erstes "id" nach "data" mit "account." Prefix ist die Account-ID
            val accountId = Regex(""""id"\s*:\s*"(account\.[^"]+)"""")
                .find(body.substringAfter("\"data\""))
                ?.groupValues?.get(1)

            if (accountId != null) {
                // In Cache speichern
                cacheFile.parentFile.mkdirs()
                cacheFile.writeText(accountId)
                println("✅ Account-ID: $accountId (gespeichert)")
            } else {
                println("❌ Account-ID nicht im Response gefunden")
            }
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
            println("🌐 Lade Match-Liste für $accountId...")

            // 1. Spieler-Endpoint → Match-ID-Liste (kein Rate-Limit-Delay nötig hier)
            val playerResponse = httpClient.send(
                buildRequest("$baseUrl/shards/$platform/players/$accountId"),
                HttpResponse.BodyHandlers.ofString()
            )
            if (playerResponse.statusCode() != 200) {
                println("❌ HTTP ${playerResponse.statusCode()} (Spielerdaten)")
                return null
            }

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
            var longestKill = 0.0
            var headshotKills = 0
            var topTens = 0
            var revives = 0
            var knockdowns = 0

            val createdAtRegex    = Regex(""""createdAt"\s*:\s*"([^"]+)"""")
            val playerIdRegex     = Regex(""""playerId"\s*:\s*"${Regex.escape(accountId)}"""")

            // Regexe für Stats-Felder
            val killsRegex        = Regex(""""kills"\s*:\s*(\d+)""")
            val winPlaceRegex     = Regex(""""winPlace"\s*:\s*(\d+)""")
            val damageRegex       = Regex(""""damageDealt"\s*:\s*([\d.]+)""")
            val assistsRegex      = Regex(""""assists"\s*:\s*(\d+)""")
            val longestKillRegex  = Regex(""""longestKill"\s*:\s*([\d.]+)""")
            val headshotRegex     = Regex(""""headshotKills"\s*:\s*(\d+)""")
            val revivesRegex      = Regex(""""revives"\s*:\s*(\d+)""")
            val knockdownsRegex   = Regex(""""DBNOs"\s*:\s*(\d+)""")

            // 2. Einzelne Matches mit Rate-Limiting durchgehen
            for ((index, matchId) in matchIds.withIndex()) {
                println("   Match ${index + 1}/${matchIds.size}: $matchId")

                val matchResponse = rateLimitedSend("$baseUrl/shards/$platform/matches/$matchId")
                if (matchResponse.statusCode() != 200) continue

                val matchBody = matchResponse.body()

                // Zeitstempel prüfen
                val createdAt = createdAtRegex.find(matchBody)?.groupValues?.get(1) ?: continue
                if (Instant.parse(createdAt) < cutoff) {
                    println("   ⏭️ Match zu alt, überspringe restliche Matches")
                    break  // Matches sind chronologisch → ältere überspringen
                }

                // Kompletten Participant-Block des Spielers extrahieren
                val includedSection = matchBody.substringAfter("\"included\"")
                val playerIdPos = playerIdRegex.find(includedSection)?.range?.first ?: continue

                // Suche den Anfang des Participant-Blocks (rückwärts von playerId)
                val blockStart = includedSection.lastIndexOf("{\"type\":\"participant\"", playerIdPos)
                    .takeIf { it >= 0 } ?: maxOf(0, playerIdPos - 3000)

                // Suche das Ende des Participant-Blocks (vorwärts von playerId)
                val blockEnd = minOf(includedSection.length, playerIdPos + 2000)
                val participantBlock = includedSection.substring(blockStart, blockEnd)

                matches++
                kills         += killsRegex.find(participantBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                assists       += assistsRegex.find(participantBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                damageDealt   += damageRegex.find(participantBlock)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                headshotKills += headshotRegex.find(participantBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                revives       += revivesRegex.find(participantBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                knockdowns    += knockdownsRegex.find(participantBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val matchLongestKill = longestKillRegex.find(participantBlock)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                if (matchLongestKill > longestKill) longestKill = matchLongestKill
                val winPlace = winPlaceRegex.find(participantBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (winPlace == 1) wins++ else deaths++
                if (winPlace <= 10) topTens++
            }

            val result = PlayerStats(wins, matches, kills, deaths, damageDealt, assists, longestKill, headshotKills, topTens, revives, knockdowns)
            println("✅ ${hours}h: ${result.extendedSummary()}")
            result

        } catch (e: Exception) {
            println("❌ Fehler (Recent Stats): ${e.message}")
            null
        }
    }
}