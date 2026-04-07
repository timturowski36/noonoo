package de.noonoo.domain.service

import de.noonoo.domain.port.input.FetchPubgDataUseCase
import de.noonoo.domain.port.output.PubgApiPort
import de.noonoo.domain.port.output.PubgRepository
import kotlinx.coroutines.delay

// PUBG API rate limit: 10 requests/minute for player and season endpoints.
// Match endpoints are unlimited. We wait 6 s between rate-limited calls to stay safe.
private const val RATE_LIMIT_DELAY_MS = 6_000L

class PubgIngestionService(
    private val apiPort: PubgApiPort,
    private val repository: PubgRepository
) : FetchPubgDataUseCase {

    override suspend fun fetchAndStore(playerNames: List<String>, platform: String) {
        // fetchPlayersByName counts against the rate limit (1 call per batch of 10)
        val allPlayers = playerNames.chunked(10).flatMapIndexed { index, batch ->
            if (index > 0) delay(RATE_LIMIT_DELAY_MS)
            apiPort.fetchPlayersByName(batch, platform)
        }
        if (allPlayers.isEmpty()) return

        repository.savePlayers(allPlayers)

        // Match endpoints are exempt from rate limiting
        val allMatchIds = allPlayers.flatMap { it.recentMatchIds }.distinct()
        val knownMatchIds = repository.findKnownMatchIds(allMatchIds)
        val newMatchIds = allMatchIds.filter { it !in knownMatchIds }

        for (matchId in newMatchIds) {
            val result = apiPort.fetchMatchDetails(matchId, platform) ?: continue
            val (match, participants) = result
            repository.saveMatch(match)
            repository.saveParticipants(participants)
        }

        // Lifetime stats endpoint counts against the rate limit (1 call per player)
        allPlayers.forEachIndexed { index, player ->
            if (index > 0) delay(RATE_LIMIT_DELAY_MS)
            val stats = apiPort.fetchLifetimeStats(player.accountId, platform)
            if (stats.isNotEmpty()) {
                repository.saveSeasonStats(stats)
            }
        }
    }
}
