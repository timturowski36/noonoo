package de.aggregator.domain.port.output

import de.aggregator.domain.model.Match
import de.aggregator.domain.model.Standing
import de.aggregator.domain.model.Team

interface MatchRepository {
    fun saveTeams(teams: List<Team>)
    fun saveMatches(matches: List<Match>)
    fun saveStandings(standings: List<Standing>)

    fun findMatchesByMatchday(league: String, season: Int, matchday: Int): List<Match>
    fun findFinishedMatchesByMatchday(league: String, season: Int, matchday: Int): List<Match>
    fun findStandings(league: String, season: Int): List<Standing>
    fun findTeamById(id: Int): Team?
    fun findCurrentMatchday(league: String, season: Int): Int
}
