package sources.bf6.api

import sources.bf6.model.Bf6Stats
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class Bf6ApiClient {
    private val httpClient = HttpClient.newHttpClient()
    private val baseUrl = "https://api.gametools.network"

    private fun buildRequest(url: String): HttpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .GET()
            .build()

    // ─────────────────────────────────────────────────────────────────────────
    // Gesamtstatistik für einen Spieler abrufen
    // ─────────────────────────────────────────────────────────────────────────

    fun fetchStats(playerName: String, platform: String = "pc"): Bf6Stats? {
        return try {
            println("🌐 [BF6] Lade Stats für '$playerName' auf $platform...")

            val encodedName = java.net.URLEncoder.encode(playerName, "UTF-8")
            val url = "$baseUrl/bf6/stats/?name=$encodedName&platform=$platform"
            val response = httpClient.send(buildRequest(url), HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                println("❌ [BF6] HTTP ${response.statusCode()}")
                return null
            }

            val body = response.body()

            // Nur den Top-Level-Block parsen (vor dem ersten "weapons"-Array)
            val topLevel = body.substringBefore("\"weapons\"")

            val userName        = Regex(""""userName"\s*:\s*"([^"]+)"""").find(topLevel)?.groupValues?.get(1) ?: playerName
            val kills           = Regex(""""kills"\s*:\s*(\d+)""").find(topLevel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val deaths          = Regex(""""deaths"\s*:\s*(\d+)""").find(topLevel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val killDeath       = Regex(""""killDeath"\s*:\s*([\d.]+)""").find(topLevel)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val wins            = Regex(""""wins"\s*:\s*(\d+)""").find(topLevel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val losses          = Regex(""""loses"\s*:\s*(\d+)""").find(topLevel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val matchesPlayed   = Regex(""""matchesPlayed"\s*:\s*(\d+)""").find(topLevel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val winPercent      = Regex(""""winPercent"\s*:\s*"([^"]+)"""").find(topLevel)?.groupValues?.get(1) ?: "-"
            val killAssists     = Regex(""""killAssists"\s*:\s*(\d+)""").find(topLevel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val headShots       = Regex(""""headShots"\s*:\s*(\d+)""").find(topLevel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val headshotPercent = Regex(""""headshots"\s*:\s*"([^"]+)"""").find(topLevel)?.groupValues?.get(1) ?: "-"
            val revives         = Regex(""""revives"\s*:\s*(\d+)""").find(topLevel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val accuracy        = Regex(""""accuracy"\s*:\s*"([^"]+)"""").find(topLevel)?.groupValues?.get(1) ?: "-"
            val timePlayed      = Regex(""""timePlayed"\s*:\s*"([^"]+)"""").find(topLevel)?.groupValues?.get(1) ?: "-"
            val killsPerMatch   = Regex(""""killsPerMatch"\s*:\s*([\d.]+)""").find(topLevel)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

            val result = Bf6Stats(
                userName, kills, deaths, killDeath, wins, losses,
                matchesPlayed, winPercent, killAssists, headShots,
                headshotPercent, revives, accuracy, timePlayed, killsPerMatch
            )
            println("✅ [BF6] ${result.userName}: ${result.matchesPlayed} Matches, K/D ${result.killDeath}")
            result

        } catch (e: Exception) {
            println("❌ [BF6] Fehler: ${e.message}")
            null
        }
    }
}
