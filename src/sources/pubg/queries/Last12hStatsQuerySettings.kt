package sources.pubg.queries

import domain.model.Query
import domain.model.QueryResult
import domain.model.QuerySettings
import sources.`pubg-api`.api.PubgApiClient
import sources.pubg.model.PlayerStats
import sources.pubg.model.PubgSettings

data class Last12hStatsQuerySettings(
    val accountId: String = "",        // Wird vorher durch AccountIdQuery aufgelöst
    val hours: Int = 12,               // Zeitfenster in Stunden
    val maxMatchesToCheck: Int = 30    // Maximale Anzahl Matches zum Durchsuchen
) : QuerySettings

class Last12hStatsQuery : Query<PubgSettings, Last12hStatsQuerySettings, PlayerStats> {

    override val name = "PUBG Letzte 12h Stats"
    override val defaultSettings = Last12hStatsQuerySettings()

    override suspend fun execute(
        moduleSettings: PubgSettings,
        querySettings: Last12hStatsQuerySettings
    ): QueryResult<PlayerStats> {
        if (querySettings.accountId.isBlank()) {
            return QueryResult.Error("Account-ID fehlt – führe zuerst AccountIdQuery aus")
        }

        val apiClient = PubgApiClient(apiKey = moduleSettings.apiKey)
        val stats = apiClient.fetchRecentStats(
            platform = moduleSettings.platform,
            accountId = querySettings.accountId,
            hours = querySettings.hours,
            maxMatches = querySettings.maxMatchesToCheck
        ) ?: return QueryResult.Error("Recent Stats nicht abrufbar")

        return QueryResult.Success(stats)
    }
}