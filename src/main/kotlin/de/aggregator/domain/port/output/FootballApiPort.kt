package de.aggregator.domain.port.output

import de.aggregator.domain.model.Match
import de.aggregator.domain.model.Standing
import de.aggregator.domain.model.Team

interface FootballApiPort {
    suspend fun fetchMatches(league: String, season: Int): List<Match>
    suspend fun fetchMatchday(league: String, season: Int, matchday: Int): List<Match>
    suspend fun fetchStandings(league: String, season: Int): List<Standing>
    suspend fun fetchTeams(league: String, season: Int): List<Team>
    suspend fun fetchLastChangeDate(league: String, season: Int, matchday: Int): String?
}
