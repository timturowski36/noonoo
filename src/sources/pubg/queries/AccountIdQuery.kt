package sources.pubg.queries

import domain.model.Query
import domain.model.QueryResult
import domain.model.QuerySettings
import sources.`pubg-api`.api.PubgApiClient
import sources.pubg.model.PlayerAccount
import sources.pubg.model.PubgSettings

data class AccountIdQuerySettings(
    val placeholder: Boolean = true
) : QuerySettings

class AccountIdQuery : Query<PubgSettings, AccountIdQuerySettings, PlayerAccount> {

    override val name = "PUBG Account-ID Lookup"
    override val defaultSettings = AccountIdQuerySettings()

    override suspend fun execute(
        moduleSettings: PubgSettings,
        querySettings: AccountIdQuerySettings
    ): QueryResult<PlayerAccount> {
        val apiClient = PubgApiClient(apiKey = moduleSettings.apiKey)

        val accountId = apiClient.fetchAccountId(
            playerName = moduleSettings.playerName,
            platform = moduleSettings.platform
        ) ?: return QueryResult.Error(
            "Account-ID nicht gefunden für '${moduleSettings.playerName}'. Prüfe den Namen."
        )

        return QueryResult.Success(
            PlayerAccount(
                accountId = accountId,
                playerName = moduleSettings.playerName,
                platform = moduleSettings.platform
            )
        )
    }
}