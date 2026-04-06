package de.noonoo.adapter.output.persistence

import de.noonoo.domain.model.PubgMatch
import de.noonoo.domain.model.PubgMatchParticipant
import de.noonoo.domain.model.PubgPlayer
import de.noonoo.domain.model.PubgSeasonStats
import de.noonoo.domain.port.output.PubgRepository
import java.sql.Connection
import java.sql.Timestamp

class DuckDbPubgRepository(
    private val connection: Connection
) : PubgRepository {

    override fun savePlayers(players: List<PubgPlayer>) {
        val sql = """
            INSERT OR REPLACE INTO pubg_players
                (account_id, name, platform, clan_id, ban_type, first_seen, last_updated)
            VALUES (?, ?, ?, ?, ?, COALESCE((SELECT first_seen FROM pubg_players WHERE account_id = ?), ?), ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            players.forEach { p ->
                val now = Timestamp.valueOf(p.lastUpdated)
                stmt.setString(1, p.accountId)
                stmt.setString(2, p.name)
                stmt.setString(3, p.platform)
                stmt.setString(4, p.clanId)
                stmt.setString(5, p.banType)
                stmt.setString(6, p.accountId)
                stmt.setTimestamp(7, now)
                stmt.setTimestamp(8, now)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun saveMatch(match: PubgMatch) {
        val sql = """
            INSERT OR IGNORE INTO pubg_matches
                (match_id, map_name, game_mode, duration, created_at, match_type, shard_id, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, match.matchId)
            stmt.setString(2, match.mapName)
            stmt.setString(3, match.gameMode)
            stmt.setInt(4, match.duration)
            stmt.setTimestamp(5, Timestamp.valueOf(match.createdAt))
            stmt.setString(6, match.matchType)
            stmt.setString(7, match.shardId)
            stmt.setTimestamp(8, Timestamp.valueOf(match.fetchedAt))
            stmt.executeUpdate()
        }
    }

    override fun saveParticipants(participants: List<PubgMatchParticipant>) {
        if (participants.isEmpty()) return
        val sql = """
            INSERT OR IGNORE INTO pubg_match_participants
                (match_id, account_id, player_name, kills, assists, dbnos, damage_dealt,
                 headshot_kills, win_place, death_type, time_survived,
                 walk_distance, ride_distance, swim_distance,
                 boosts, heals, revives, weapons_acquired,
                 kill_place, kill_streaks, longest_kill)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            participants.forEach { p ->
                stmt.setString(1, p.matchId)
                stmt.setString(2, p.accountId)
                stmt.setString(3, p.playerName)
                stmt.setInt(4, p.kills)
                stmt.setInt(5, p.assists)
                stmt.setInt(6, p.dbnos)
                stmt.setDouble(7, p.damageDealt)
                stmt.setInt(8, p.headshotKills)
                stmt.setInt(9, p.winPlace)
                stmt.setString(10, p.deathType)
                stmt.setDouble(11, p.timeSurvived)
                stmt.setDouble(12, p.walkDistance)
                stmt.setDouble(13, p.rideDistance)
                stmt.setDouble(14, p.swimDistance)
                stmt.setInt(15, p.boosts)
                stmt.setInt(16, p.heals)
                stmt.setInt(17, p.revives)
                stmt.setInt(18, p.weaponsAcquired)
                stmt.setInt(19, p.killPlace)
                stmt.setInt(20, p.killStreaks)
                stmt.setDouble(21, p.longestKill)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun saveSeasonStats(stats: List<PubgSeasonStats>) {
        if (stats.isEmpty()) return
        val sql = """
            INSERT OR REPLACE INTO pubg_season_stats
                (account_id, platform, season_id, game_mode,
                 kills, assists, dbnos, damage_dealt, wins, top10s,
                 rounds_played, losses, headshot_kills, longest_kill,
                 round_most_kills, walk_distance, ride_distance,
                 boosts, heals, revives, team_kills, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stats.forEach { s ->
                stmt.setString(1, s.accountId)
                stmt.setString(2, s.platform)
                stmt.setString(3, s.seasonId)
                stmt.setString(4, s.gameMode)
                stmt.setInt(5, s.kills)
                stmt.setInt(6, s.assists)
                stmt.setInt(7, s.dbnos)
                stmt.setDouble(8, s.damageDealt)
                stmt.setInt(9, s.wins)
                stmt.setInt(10, s.top10s)
                stmt.setInt(11, s.roundsPlayed)
                stmt.setInt(12, s.losses)
                stmt.setInt(13, s.headshotKills)
                stmt.setDouble(14, s.longestKill)
                stmt.setInt(15, s.roundMostKills)
                stmt.setDouble(16, s.walkDistance)
                stmt.setDouble(17, s.rideDistance)
                stmt.setInt(18, s.boosts)
                stmt.setInt(19, s.heals)
                stmt.setInt(20, s.revives)
                stmt.setInt(21, s.teamKills)
                stmt.setTimestamp(22, Timestamp.valueOf(s.fetchedAt))
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun findKnownMatchIds(matchIds: List<String>): Set<String> {
        if (matchIds.isEmpty()) return emptySet()
        val placeholders = matchIds.joinToString(",") { "?" }
        val sql = "SELECT match_id FROM pubg_matches WHERE match_id IN ($placeholders)"
        return connection.prepareStatement(sql).use { stmt ->
            matchIds.forEachIndexed { i, id -> stmt.setString(i + 1, id) }
            stmt.executeQuery().use { rs ->
                val result = mutableSetOf<String>()
                while (rs.next()) result.add(rs.getString("match_id"))
                result
            }
        }
    }
}
