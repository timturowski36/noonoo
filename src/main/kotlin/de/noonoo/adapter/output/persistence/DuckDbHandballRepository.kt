package de.noonoo.adapter.output.persistence

import de.noonoo.domain.model.HandballMatch
import de.noonoo.domain.model.HandballStanding
import de.noonoo.domain.model.HandballTickerEvent
import de.noonoo.domain.port.output.HandballRepository
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime

class DuckDbHandballRepository(private val connection: Connection) : HandballRepository {

    override fun saveMatches(matches: List<HandballMatch>) {
        if (matches.isEmpty()) return
        val sql = """
            INSERT OR REPLACE INTO handball_matches (
                id, game_no, league_id, league_name,
                home_team, guest_team,
                kickoff_date, kickoff_time,
                home_goals_ft, guest_goals_ft,
                home_goals_ht, guest_goals_ht,
                home_points, guest_points,
                venue_name, venue_town,
                is_finished, fetched_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            for (m in matches) {
                stmt.setLong(1, m.id)
                stmt.setString(2, m.gameNo)
                stmt.setString(3, m.leagueId)
                stmt.setString(4, m.leagueShortName)
                stmt.setString(5, m.homeTeam)
                stmt.setString(6, m.guestTeam)
                stmt.setString(7, m.kickoffDate)
                stmt.setString(8, m.kickoffTime)
                stmt.setObject(9, m.homeGoalsFt)
                stmt.setObject(10, m.guestGoalsFt)
                stmt.setObject(11, m.homeGoalsHt)
                stmt.setObject(12, m.guestGoalsHt)
                stmt.setObject(13, m.homePoints)
                stmt.setObject(14, m.guestPoints)
                stmt.setString(15, m.venueName)
                stmt.setString(16, m.venueTown)
                stmt.setBoolean(17, m.isFinished)
                stmt.setTimestamp(18, Timestamp.valueOf(m.fetchedAt))
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun saveStandings(standings: List<HandballStanding>) {
        if (standings.isEmpty()) return
        val sql = """
            INSERT OR REPLACE INTO handball_standings (
                league_id, position, team_name,
                played, won, draw, lost,
                goals_for, goals_against,
                points_plus, points_minus,
                fetched_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            for (s in standings) {
                stmt.setString(1, s.leagueId)
                stmt.setInt(2, s.position)
                stmt.setString(3, s.teamName)
                stmt.setInt(4, s.played)
                stmt.setInt(5, s.won)
                stmt.setInt(6, s.draw)
                stmt.setInt(7, s.lost)
                stmt.setInt(8, s.goalsFor)
                stmt.setInt(9, s.goalsAgainst)
                stmt.setInt(10, s.pointsPlus)
                stmt.setInt(11, s.pointsMinus)
                stmt.setTimestamp(12, Timestamp.valueOf(s.fetchedAt))
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun saveTickerEvents(events: List<HandballTickerEvent>) {
        if (events.isEmpty()) return
        val sql = """
            INSERT OR REPLACE INTO handball_ticker_events (
                match_id, game_minute, event_type,
                home_score, away_score,
                description, fetched_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            for (e in events) {
                stmt.setLong(1, e.matchId)
                stmt.setString(2, e.gameMinute)
                stmt.setString(3, e.eventType)
                stmt.setObject(4, e.homeScore)
                stmt.setObject(5, e.awayScore)
                stmt.setString(6, e.description)
                stmt.setTimestamp(7, Timestamp.valueOf(e.fetchedAt))
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun findMatchesByLeague(leagueId: String): List<HandballMatch> {
        val sql = "SELECT * FROM handball_matches WHERE league_id = ? ORDER BY kickoff_date, kickoff_time"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, leagueId)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<HandballMatch>()
                while (rs.next()) {
                    result += HandballMatch(
                        id = rs.getLong("id"),
                        gameNo = rs.getString("game_no") ?: "",
                        leagueId = rs.getString("league_id"),
                        leagueShortName = rs.getString("league_name") ?: "",
                        homeTeam = rs.getString("home_team"),
                        guestTeam = rs.getString("guest_team"),
                        kickoffDate = rs.getString("kickoff_date") ?: "",
                        kickoffTime = rs.getString("kickoff_time") ?: "",
                        homeGoalsFt = rs.getObject("home_goals_ft") as? Int,
                        guestGoalsFt = rs.getObject("guest_goals_ft") as? Int,
                        homeGoalsHt = rs.getObject("home_goals_ht") as? Int,
                        guestGoalsHt = rs.getObject("guest_goals_ht") as? Int,
                        homePoints = rs.getObject("home_points") as? Int,
                        guestPoints = rs.getObject("guest_points") as? Int,
                        venueName = rs.getString("venue_name") ?: "",
                        venueTown = rs.getString("venue_town") ?: "",
                        isFinished = rs.getBoolean("is_finished"),
                        fetchedAt = rs.getTimestamp("fetched_at").toLocalDateTime()
                    )
                }
                return result
            }
        }
    }
}
