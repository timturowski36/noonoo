package sources.bundesliga.api

import sources.bundesliga.model.TabellenEintrag
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class TabellenApiClient(
    private val baseUrl: String = "https://api.openligadb.de"
) {
    private val httpClient = HttpClient.newHttpClient()

    fun fetchTabelle(
        liga: String,
        saison: String
    ): List<TabellenEintrag> {
        return try {
            println("🌐 Lade Tabelle für $liga / $saison")
            val url = "$baseUrl/getbltable/$liga/$saison"
            println("📍 URL: $url")

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200 || response.body().isBlank()) {
                println("❌ HTTP Fehler (statusCode=${response.statusCode()})")
                return emptyList()
            }

            parseTabelle(response.body(), liga)
        } catch (e: Exception) {
            println("❌ Fehler beim Abrufen der Tabelle: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseTabelle(json: String, liga: String): List<TabellenEintrag> {
        val teamInfoIdRegex = Regex(""""teamInfoId"\s*:\s*(\d+)""")
        val teamNameRegex = Regex(""""teamName"\s*:\s*"([^"]+)"""")
        val shortNameRegex = Regex(""""shortName"\s*:\s*"([^"]+)"""")
        val pointsRegex = Regex(""""points"\s*:\s*(\d+)""")
        val opponentGoalsRegex = Regex(""""opponentGoals"\s*:\s*(\d+)""")
        val goalsRegex = Regex(""""goals"\s*:\s*(\d+)""")
        val matchesRegex = Regex(""""matches"\s*:\s*(\d+)""")
        val wonRegex = Regex(""""won"\s*:\s*(\d+)""")
        val lostRegex = Regex(""""lost"\s*:\s*(\d+)""")
        val drawRegex = Regex(""""draw"\s*:\s*(\d+)""")
        val goalDiffRegex = Regex(""""goalDiff"\s*:\s*(-?\d+)""")

        val eintraege = json
            .split("\"teamInfoId\"")
            .drop(1)
            .mapIndexedNotNull { index, block ->
                val teamInfoId = teamInfoIdRegex.find("\"teamInfoId\"$block")?.groupValues?.get(1)?.toIntOrNull()
                val teamName = teamNameRegex.find(block)?.groupValues?.get(1)
                val shortName = shortNameRegex.find(block)?.groupValues?.get(1)
                val points = pointsRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()
                val opponentGoals = opponentGoalsRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()
                val goals = goalsRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()
                val matches = matchesRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()
                val won = wonRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()
                val lost = lostRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()
                val draw = drawRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()
                val goalDiff = goalDiffRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()

                if (teamInfoId == null || teamName == null || shortName == null ||
                    points == null || opponentGoals == null || goals == null ||
                    matches == null || won == null || lost == null ||
                    draw == null || goalDiff == null
                ) {
                    return@mapIndexedNotNull null
                }

                TabellenEintrag(
                    liga = liga,
                    platz = index + 1,
                    teamInfoId = teamInfoId,
                    teamName = teamName,
                    shortName = shortName,
                    points = points,
                    opponentGoals = opponentGoals,
                    goals = goals,
                    matches = matches,
                    won = won,
                    lost = lost,
                    draw = draw,
                    goalDiff = goalDiff
                )
            }

        println("✅ ${eintraege.size} Tabelleneinträge gefunden")
        return eintraege
    }
}