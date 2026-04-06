package de.noonoo.adapter.output.api

import de.noonoo.domain.model.Goal
import de.noonoo.domain.model.GoalGetter
import de.noonoo.domain.model.Match
import de.noonoo.domain.model.Standing
import de.noonoo.domain.model.Team
import de.noonoo.domain.port.output.FootballApiPort
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class OpenLigaDbClient(
    private val httpClient: HttpClient
) : FootballApiPort {

    private val baseUrl = "https://api.openligadb.de"
    private val formatter = DateTimeFormatter.ISO_DATE_TIME

    override suspend fun fetchMatches(league: String, season: Int): List<Match> {
        val response: List<ApiMatch> = httpClient.get("$baseUrl/getmatchdata/$league/$season").body()
        return response.map { it.toDomain() }
    }

    override suspend fun fetchMatchday(league: String, season: Int, matchday: Int): List<Match> {
        val response: List<ApiMatch> = httpClient.get("$baseUrl/getmatchdata/$league/$season/$matchday").body()
        return response.map { it.toDomain() }
    }

    override suspend fun fetchStandings(league: String, season: Int): List<Standing> {
        val response: List<ApiStanding> = httpClient.get("$baseUrl/getbltable/$league/$season").body()
        return response.mapIndexed { index, it -> it.toDomain(league, season, index + 1) }
    }

    override suspend fun fetchTeams(league: String, season: Int): List<Team> {
        val response: List<ApiTeam> = httpClient.get("$baseUrl/getavailableteams/$league/$season").body()
        return response.map { it.toDomain() }
    }

    override suspend fun fetchLastChangeDate(league: String, season: Int, matchday: Int): String? {
        return try {
            httpClient.get("$baseUrl/getlastchangedate/$league/$season/$matchday").body<String>()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchGoalGetters(league: String, season: Int): List<GoalGetter> {
        val response: List<ApiGoalGetter> = httpClient.get("$baseUrl/getgoalgetters/$league/$season").body()
        return response.map { it.toDomain() }
    }

    // ── API DTOs ──────────────────────────────────────────────────────────────

    @Serializable
    private data class ApiTeam(
        @SerialName("TeamId") val id: Int,
        @SerialName("TeamName") val name: String,
        @SerialName("ShortName") val shortName: String = "",
        @SerialName("TeamIconUrl") val iconUrl: String = ""
    ) {
        fun toDomain() = Team(id = id, name = name, shortName = shortName, iconUrl = iconUrl)
    }

    @Serializable
    private data class ApiGoal(
        @SerialName("GoalID") val id: Int,
        @SerialName("ScoreTeam1") val scoreHome: Int,
        @SerialName("ScoreTeam2") val scoreAway: Int,
        @SerialName("MatchMinute") val minute: Int? = null,
        @SerialName("GoalGetterName") val scorerName: String = "",
        @SerialName("IsOwnGoal") val isOwnGoal: Boolean = false,
        @SerialName("IsPenalty") val isPenalty: Boolean = false
    ) {
        fun toDomain(matchId: Int) = Goal(
            id = id,
            matchId = matchId,
            scorerName = scorerName,
            minute = minute ?: 0,
            isOwnGoal = isOwnGoal,
            isPenalty = isPenalty,
            scoreHome = scoreHome,
            scoreAway = scoreAway
        )
    }

    @Serializable
    private data class ApiMatch(
        @SerialName("MatchID") val id: Int,
        @SerialName("LeagueShortcut") val league: String,
        @SerialName("LeagueSeason") val season: Int,
        @SerialName("MatchDateTimeUTC") val kickoffAt: String,
        @SerialName("Group") val group: ApiGroup? = null,
        @SerialName("Team1") val homeTeam: ApiTeam,
        @SerialName("Team2") val awayTeam: ApiTeam,
        @SerialName("MatchIsFinished") val isFinished: Boolean,
        @SerialName("MatchResults") val results: List<ApiResult> = emptyList(),
        @SerialName("Goals") val goals: List<ApiGoal> = emptyList()
    ) {
        fun toDomain(): Match {
            val ht = results.firstOrNull { it.resultTypeId == 1 }
            val ft = results.firstOrNull { it.resultTypeId == 2 }
            return Match(
                id = id,
                league = league,
                season = season,
                matchday = group?.groupOrderId ?: 0,
                homeTeamId = homeTeam.id,
                awayTeamId = awayTeam.id,
                kickoffAt = LocalDateTime.parse(kickoffAt.trimEnd('Z'), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                homeScoreHt = ht?.pointsTeam1,
                awayScoreHt = ht?.pointsTeam2,
                homeScoreFt = ft?.pointsTeam1,
                awayScoreFt = ft?.pointsTeam2,
                isFinished = isFinished,
                fetchedAt = LocalDateTime.now(),
                goals = goals.map { it.toDomain(id) }
            )
        }
    }

    @Serializable
    private data class ApiGroup(
        @SerialName("GroupOrderID") val groupOrderId: Int,
        @SerialName("GroupName") val groupName: String = ""
    )

    @Serializable
    private data class ApiResult(
        @SerialName("ResultTypeID") val resultTypeId: Int,
        @SerialName("PointsTeam1") val pointsTeam1: Int,
        @SerialName("PointsTeam2") val pointsTeam2: Int
    )

    @Serializable
    private data class ApiGoalGetter(
        @SerialName("GoalGetterName") val name: String = "",
        @SerialName("GoalCount") val goals: Int = 0,
        @SerialName("GoalGetterTeamID") val teamId: Int = 0
    ) {
        fun toDomain() = GoalGetter(name = name, teamId = teamId, goals = goals)
    }

    @Serializable
    private data class ApiStanding(
        @SerialName("TeamInfoId") val teamId: Int,
        @SerialName("Points") val points: Int,
        @SerialName("Won") val won: Int,
        @SerialName("Lost") val lost: Int,
        @SerialName("Draw") val draw: Int,
        @SerialName("Goals") val goalsFor: Int,
        @SerialName("OpponentGoals") val goalsAgainst: Int,
        @SerialName("Matches") val played: Int
    ) {
        fun toDomain(league: String, season: Int, position: Int) = Standing(
            league = league,
            season = season,
            position = position,
            teamId = teamId,
            played = played,
            won = won,
            draw = draw,
            lost = lost,
            goalsFor = goalsFor,
            goalsAgainst = goalsAgainst,
            points = points,
            fetchedAt = LocalDateTime.now()
        )
    }
}
