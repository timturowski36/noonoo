package de.aggregator.domain.port.input

import de.aggregator.domain.model.Match
import de.aggregator.domain.model.Standing
import de.aggregator.domain.model.Team

interface QueryDataUseCase {
    fun getStandings(league: String, season: Int): List<Standing>
    fun getMatchdayResults(league: String, season: Int, matchday: Int): List<Match>
    fun getCurrentMatchday(league: String, season: Int): Int
    fun getTeam(id: Int): Team?
}
