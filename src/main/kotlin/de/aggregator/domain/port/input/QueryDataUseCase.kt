package de.aggregator.domain.port.input

import de.aggregator.domain.model.GoalGetter
import de.aggregator.domain.model.Match
import de.aggregator.domain.model.Standing
import de.aggregator.domain.model.Team

interface QueryDataUseCase {
    fun getStandings(league: String, season: Int): List<Standing>
    fun getMatchdayResults(league: String, season: Int, matchday: Int): List<Match>
    fun getMatchday(league: String, season: Int, matchday: Int): List<Match>
    fun getCurrentMatchday(league: String, season: Int): Int
    fun getNextMatchday(league: String, season: Int): Int
    fun getTeam(id: Int): Team?
    fun getTeamByName(name: String): Team?
    fun getLastMatchesByTeam(league: String, season: Int, teamId: Int, limit: Int): List<Match>
    fun getNextMatchesByTeam(league: String, season: Int, teamId: Int, limit: Int): List<Match>
    suspend fun getGoalGetters(league: String, season: Int): List<GoalGetter>
}
