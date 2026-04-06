package de.aggregator.domain.service

import de.aggregator.domain.port.input.FetchDataUseCase
import de.aggregator.domain.port.output.FootballApiPort
import de.aggregator.domain.port.output.MatchRepository

class IngestionService(
    private val apiPort: FootballApiPort,
    private val repository: MatchRepository
) : FetchDataUseCase {

    override suspend fun fetchAndStore(league: String, season: Int) {
        val teams = apiPort.fetchTeams(league, season)
        repository.saveTeams(teams)

        val matches = apiPort.fetchMatches(league, season)
        repository.saveMatches(matches)

        val standings = apiPort.fetchStandings(league, season)
        repository.saveStandings(standings)
    }
}
