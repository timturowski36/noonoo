package de.noonoo.domain.port.output

import de.noonoo.domain.model.PubgMatch
import de.noonoo.domain.model.PubgMatchParticipant
import de.noonoo.domain.model.PubgPlayer
import de.noonoo.domain.model.PubgSeasonStats

interface PubgRepository {
    fun savePlayers(players: List<PubgPlayer>)
    fun saveMatch(match: PubgMatch)
    fun saveParticipants(participants: List<PubgMatchParticipant>)
    fun saveSeasonStats(stats: List<PubgSeasonStats>)
    fun findKnownMatchIds(matchIds: List<String>): Set<String>
}
