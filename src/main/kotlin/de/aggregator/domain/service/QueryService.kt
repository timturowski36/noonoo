package de.aggregator.domain.service

import de.aggregator.domain.model.Match
import de.aggregator.domain.model.Standing
import de.aggregator.domain.model.Team
import de.aggregator.domain.port.input.QueryDataUseCase
import de.aggregator.domain.port.output.MatchRepository

class QueryService(
    private val repository: MatchRepository
) : QueryDataUseCase {

    override fun getStandings(league: String, season: Int): List<Standing> =
        repository.findStandings(league, season)

    override fun getMatchdayResults(league: String, season: Int, matchday: Int): List<Match> =
        repository.findFinishedMatchesByMatchday(league, season, matchday)

    override fun getCurrentMatchday(league: String, season: Int): Int =
        repository.findCurrentMatchday(league, season)

    override fun getTeam(id: Int): Team? =
        repository.findTeamById(id)
}
