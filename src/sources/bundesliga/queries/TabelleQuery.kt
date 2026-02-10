package sources.bundesliga.queries

import domain.model.Query
import domain.model.QueryResult
import domain.model.QuerySettings
import sources.bundesliga.api.TabellenApiClient
import sources.bundesliga.model.BundesligaSettings
import sources.bundesliga.model.TabellenEintrag

data class TabelleQuerySettings(
    val anzahlTeams: Int = 3,
    val nurLieblingsverein: Boolean = false
) : QuerySettings

class TabelleQuery : Query<BundesligaSettings, TabelleQuerySettings, List<TabellenEintrag>> {

    override val name: String = "Tabelle"
    override val defaultSettings = TabelleQuerySettings()

    private val apiClient = TabellenApiClient()

    override suspend fun execute(
        moduleSettings: BundesligaSettings,
        querySettings: TabelleQuerySettings
    ): QueryResult<List<TabellenEintrag>> {
        return try {
            val saison = ermittleAktuelleSaison()

            val alleEintraege = apiClient.fetchTabelle(
                liga = moduleSettings.liga,
                saison = saison
            )

            if (alleEintraege.isEmpty()) {
                return QueryResult.Error("Keine Tabellendaten gefunden für ${moduleSettings.liga}")
            }

            // Tabellenplätze hinzufügen (1-basiert)
            val eintraegeMitPlatz = alleEintraege.mapIndexed { index, eintrag ->
                eintrag.copy(platz = index + 1)
            }

            val gefilterteEintraege = when {
                querySettings.nurLieblingsverein -> {
                    eintraegeMitPlatz.filter { eintrag ->
                        eintrag.shortName.equals(moduleSettings.lieblingsverein, ignoreCase = true) ||
                                eintrag.teamName.contains(moduleSettings.lieblingsverein, ignoreCase = true)
                    }
                }
                else -> {
                    eintraegeMitPlatz.take(querySettings.anzahlTeams)
                }
            }

            QueryResult.Success(gefilterteEintraege)
        } catch (e: Exception) {
            QueryResult.Error("Fehler beim Laden der Tabelle: ${e.message}")
        }
    }

    private fun ermittleAktuelleSaison(): String {
        val currentYear = java.time.LocalDate.now().year
        val currentMonth = java.time.LocalDate.now().monthValue

        // Bundesliga-Saison läuft von August bis Mai
        // Wenn wir zwischen Januar und Juli sind, ist es die Saison des Vorjahres
        return if (currentMonth >= 8) {
            currentYear.toString()
        } else {
            (currentYear - 1).toString()
        }
    }
}