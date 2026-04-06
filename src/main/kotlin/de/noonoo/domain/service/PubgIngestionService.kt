package de.noonoo.domain.service

import de.noonoo.domain.port.input.FetchPubgDataUseCase
import de.noonoo.domain.port.output.PubgApiPort
import de.noonoo.domain.port.output.PubgRepository

class PubgIngestionService(
    private val apiPort: PubgApiPort,
    private val repository: PubgRepository
) : FetchPubgDataUseCase {

    override suspend fun fetchAndStore(playerNames: List<String>, platform: String) {
        val allPlayers = playerNames.chunked(10).flatMap { batch ->
            apiPort.fetchPlayersByName(batch, platform)
        }
        if (allPlayers.isEmpty()) return

        repository.savePlayers(allPlayers)

        val allMatchIds = allPlayers.flatMap { it.recentMatchIds }.distinct()
        val knownMatchIds = repository.findKnownMatchIds(allMatchIds)
        val newMatchIds = allMatchIds.filter { it !in knownMatchIds }

        for (matchId in newMatchIds) {
            val result = apiPort.fetchMatchDetails(matchId, platform) ?: continue
            val (match, participants) = result
            repository.saveMatch(match)
            repository.saveParticipants(participants)
        }

        for (player in allPlayers) {
            val stats = apiPort.fetchLifetimeStats(player.accountId, platform)
            if (stats.isNotEmpty()) {
                repository.saveSeasonStats(stats)
            }
        }
    }
}
