package de.noonoo.domain.port.output

import de.noonoo.domain.model.HandballMatch
import de.noonoo.domain.model.HandballStanding
import de.noonoo.domain.model.HandballTickerEvent

interface HandballApiPort {
    suspend fun fetchTeamSchedule(): List<HandballMatch>
    suspend fun fetchLeagueTable(leagueId: String): List<HandballStanding>
    suspend fun fetchMatchTicker(gameId: Long): List<HandballTickerEvent>
}
