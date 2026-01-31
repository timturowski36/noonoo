package sources.bundesliga.queries

import domain.model.Query
import domain.model.QueryResult
import domain.model.QuerySettings
import sources.bundesliga.api.TabellenApiClient
import sources.bundesliga.model.BundesligaSettings
import sources.bundesliga.model.TabellenEintrag
import java.time.LocalDate

data class Top4TabellenQuerySettings(
    val anzahlTeamsProLiga: Int = 3
) : QuerySettings

class Top5TabellenQuery : Query<BundesligaSettings, Top4TabellenQuerySettings, List<TabellenEintrag>> {

    override val name: String = "Top 4 Ligen Tabelle"
    override val defaultSettings = Top4TabellenQuerySettings()

    private val apiClient = TabellenApiClient()

    private val top5Ligen = listOf(
        "bl1",
        "bl2",
        "dfb",
        "ucl",
    )

    override suspend fun execute(
        moduleSettings: BundesligaSettings,
        querySettings: Top4TabellenQuerySettings
    ): QueryResult<List<TabellenEintrag>> {
        return try {
            val saison = ermittleAktuelleSaison()
            val alleTopTeams = mutableListOf<TabellenEintrag>()

            for (liga in top5Ligen) {
                val tabellenEintraege = apiClient.fetchTabelle(
                    liga = liga,
                    saison = saison
                )

                val topTeams = tabellenEintraege.take(querySettings.anzahlTeamsProLiga)
                alleTopTeams.addAll(topTeams)

                if (topTeams.isNotEmpty()) {
                    println("✅ $liga: ${topTeams.size} Teams geladen")
                } else {
                    println("⚠️ $liga: Keine Daten verfügbar")
                }
            }

            if (alleTopTeams.isEmpty()) {
                QueryResult.Error("Keine Tabellendaten gefunden")
            } else {
                QueryResult.Success(alleTopTeams)
            }
        } catch (e: Exception) {
            QueryResult.Error("Fehler beim Laden der Tabellen: ${e.message}")
        }
    }

    private fun ermittleAktuelleSaison(): String {
        val currentYear = LocalDate.now().year
        val currentMonth = LocalDate.now().monthValue
        return if (currentMonth >= 8) {
            currentYear.toString()
        } else {
            (currentYear - 1).toString()
        }
    }
}