package sources.bundesliga.queries

import domain.model.Query
import domain.model.QueryResult
import domain.model.QuerySettings
import sources.bundesliga.api.BundesligaApiClient
import sources.bundesliga.model.BundesligaSettings
import sources.bundesliga.model.Spiel

data class NaechsteSpieleQuerySettings(
    val anzahlSpiele: Int = 3,
    val nurHeimspiele: Boolean = false
) : QuerySettings

class NaechsteSpieleQuery(
    private val apiClient: BundesligaApiClient = BundesligaApiClient()
) : Query<BundesligaSettings, NaechsteSpieleQuerySettings, List<Spiel>> {

    override val name: String = "Nächste Spiele Lieblingsverein"
    override val defaultSettings = NaechsteSpieleQuerySettings()

    override suspend fun execute(
        moduleSettings: BundesligaSettings,
        querySettings: NaechsteSpieleQuerySettings
    ): QueryResult<List<Spiel>> {

        val alleSpiele = apiClient.fetchNaechsteSpiele(
            team = moduleSettings.lieblingsverein,
            liga = moduleSettings.liga,
            saison = "2025"
        )

        val gefiltert = alleSpiele
            .asSequence()
            .let { seq ->
                if (querySettings.nurHeimspiele) {
                    seq.filter {
                        it.heimmannschaft.equals(
                            moduleSettings.lieblingsverein,
                            ignoreCase = true
                        )
                    }
                } else {
                    seq
                }
            }
            .take(querySettings.anzahlSpiele)
            .toList()

        return QueryResult.Success(gefiltert)
    }
}