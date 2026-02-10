package sources.bundesliga.api

import sources.bundesliga.model.Spiel
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class BundesligaApiClient(
    private val baseUrl: String = "https://api.openligadb.de"
) {
    private val httpClient = HttpClient.newHttpClient()

    fun fetchNaechsteSpiele(
        team: String,
        liga: String,
        saison: String
    ): List<Spiel> {

        return try {
            println("🌐 Lade Spiele für $team ($liga / $saison)")

            val encodedTeam = URLEncoder.encode(team, StandardCharsets.UTF_8)
            val url = "$baseUrl/getmatchdata/$liga/$saison/$encodedTeam"

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

            parseSpiele(response.body())

        } catch (e: Exception) {
            println("❌ Fehler beim Abrufen der Spiele: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseSpiele(json: String): List<Spiel> {

        val team1Regex = Regex(""""team1".*?"teamName"\s*:\s*"([^"]+)"""")
        val team2Regex = Regex(""""team2".*?"teamName"\s*:\s*"([^"]+)"""")
        val dateRegex = Regex(""""matchDateTimeUTC"\s*:\s*"([^"]+)"""")
        val finishedRegex = Regex(""""matchIsFinished"\s*:\s*(true|false)""")
        val spieltagRegex = Regex(""""groupOrderID"\s*:\s*(\d+)""")

        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        val spiele = json
            .split("\"matchID\"")
            .drop(1)
            .mapNotNull { block ->

                val heim = team1Regex.find(block)?.groupValues?.get(1)
                val gast = team2Regex.find(block)?.groupValues?.get(1)
                val datumRaw = dateRegex.find(block)?.groupValues?.get(1)
                val finished = finishedRegex.find(block)?.groupValues?.get(1)?.toBoolean()
                val spieltag = spieltagRegex.find(block)?.groupValues?.get(1)?.toInt() ?: 0

                if (heim == null || gast == null || datumRaw == null || finished != false) {
                    return@mapNotNull null
                }

                val datum = OffsetDateTime.parse(datumRaw)
                    .toLocalDate()
                    .format(formatter)

                Spiel(
                    datum = datum,
                    heimmannschaft = heim,
                    gastmannschaft = gast,
                    spieltag = spieltag
                )
            }

        println("✅ ${spiele.size} kommende Spiele gefunden")
        return spiele
    }
}