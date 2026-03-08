package sources.pubg.queries

import domain.model.Query
import domain.model.QueryResult
import domain.model.QuerySettings
import sources.`pubg-api`.api.PubgApiClient
import sources.pubg.model.PubgSettings


data class TotalWinsQuerySettings(
    val accountId: String = ""  // Wird vorher durch AccountIdQuery aufgelöst
) : QuerySettings

class TotalWinsQuery : Query<PubgSettings, TotalWinsQuerySettings, Int> {

    override val name = "PUBG Lifetime Wins"
    override val defaultSettings = TotalWinsQuerySettings()

    override suspend fun execute(
        moduleSettings: PubgSettings,
        querySettings: TotalWinsQuerySettings
    ): QueryResult<Int> {
        if (querySettings.accountId.isBlank()) {
            return QueryResult.Error("Account-ID fehlt – führe zuerst AccountIdQuery aus")
        }

        val apiClient = PubgApiClient(apiKey = moduleSettings.apiKey)
        val wins = apiClient.fetchLifetimeWins(
            platform = moduleSettings.platform,
            accountId = querySettings.accountId
        ) ?: return QueryResult.Error("Lifetime Wins nicht abrufbar")

        return QueryResult.Success(wins)
    }
}