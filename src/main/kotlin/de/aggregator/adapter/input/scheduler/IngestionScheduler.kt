package de.aggregator.adapter.input.scheduler

import de.aggregator.adapter.config.ModuleConfig
import de.aggregator.adapter.config.OutputConfig
import de.aggregator.adapter.output.discord.DiscordSender
import de.aggregator.domain.model.Standing
import de.aggregator.domain.model.Team
import de.aggregator.domain.port.input.FetchDataUseCase
import de.aggregator.domain.port.input.QueryDataUseCase
import de.aggregator.domain.port.output.NotificationPort
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

class IngestionScheduler(
    private val modules: List<ModuleConfig>,
    private val fetchUseCase: FetchDataUseCase,
    private val queryUseCase: QueryDataUseCase,
    private val notificationPort: NotificationPort,
    private val webhookChannels: Map<String, String>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        modules.filter { it.enabled }.forEach { module ->
            startIngestion(module)
            startOutputSchedules(module)
        }
    }

    fun stop() {
        scope.cancel()
    }

    // ── Ingestion ─────────────────────────────────────────────────────────────

    private fun startIngestion(module: ModuleConfig) {
        val league = module.config["league"] ?: return
        val season = module.config["season"]?.toInt() ?: return
        val intervalMs = module.schedule.fetchIntervalMinutes * 60_000L

        scope.launch {
            while (isActive) {
                try {
                    log.info { "[${module.id}] Starte Datenabruf ($league/$season)..." }
                    fetchUseCase.fetchAndStore(league, season)
                    log.info { "[${module.id}] Datenabruf abgeschlossen." }
                } catch (e: Exception) {
                    log.error(e) { "[${module.id}] Fehler beim Datenabruf: ${e.message}" }
                }
                delay(intervalMs)
            }
        }
    }

    // ── Output-Schedules ──────────────────────────────────────────────────────

    private fun startOutputSchedules(module: ModuleConfig) {
        val league = module.config["league"] ?: return
        val season = module.config["season"]?.toInt() ?: return

        module.outputs.forEach { output ->
            scope.launch {
                while (isActive) {
                    val delayMs = millisUntilNext(output.schedule)
                    log.info { "[${module.id}] Nächste Ausgabe '${output.format}' in ${delayMs / 60_000}min." }
                    delay(delayMs)
                    try {
                        sendOutput(module.id, output, league, season)
                    } catch (e: Exception) {
                        log.error(e) { "[${module.id}] Fehler bei Ausgabe '${output.format}': ${e.message}" }
                    }
                }
            }
        }
    }

    private suspend fun sendOutput(moduleId: String, output: OutputConfig, league: String, season: Int) {
        val teamNames = output.teams
        if (!teamNames.isNullOrEmpty() && isTeamSpecificFormat(output.format)) {
            // Multi-Team: eine Nachricht pro Team
            for (teamName in teamNames) {
                val team = queryUseCase.getTeamByName(teamName)
                if (team == null) {
                    log.warn { "[$moduleId] Team '$teamName' nicht in DB gefunden – übersprungen." }
                    continue
                }
                val message = buildMessage(output, league, season, team.id, team.shortName)
                if (message != null) {
                    notificationPort.send(output.channel, message)
                    log.info { "[$moduleId] '${output.format}' für '${team.shortName}' gesendet → #${output.channel}." }
                    delay(500)
                }
            }
        } else {
            val message = buildMessage(output, league, season, null, null)
            if (message != null) {
                notificationPort.send(output.channel, message)
                log.info { "[$moduleId] '${output.format}' gesendet → #${output.channel}." }
            }
        }
    }

    private fun isTeamSpecificFormat(format: String) = format in setOf(
        "team_last_matches", "team_next_matches", "team_top_scorers", "team_summary"
    )

    // ── Message Builder ───────────────────────────────────────────────────────

    private suspend fun buildMessage(
        output: OutputConfig,
        league: String,
        season: Int,
        resolvedTeamId: Int?,
        resolvedTeamName: String?
    ): String? {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        val leagueName = output.params?.get("leagueName") ?: league
        val limit = output.params?.get("limit")?.toIntOrNull() ?: 5

        // teamId: aus Multi-Team-Auflösung, oder aus einzelnem params.teamId
        val teamId = resolvedTeamId
            ?: output.params?.get("teamId")?.toIntOrNull()

        return when (output.format) {

            "table_summary" -> {
                val standings = queryUseCase.getStandings(league, season)
                if (standings.isEmpty()) return null
                val teams = resolveTeams(standings.map { it.teamId })
                DiscordSender.formatTableSummary(standings, teams, leagueName, now)
            }

            "matchday_results" -> {
                val matchday = queryUseCase.getCurrentMatchday(league, season)
                val matches = queryUseCase.getMatchdayResults(league, season, matchday)
                if (matches.isEmpty()) return null
                val teams = resolveTeams(matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatWeekendSummary(matches, teams, matchday, leagueName, now)
            }

            "team_last_matches" -> {
                val id = teamId ?: return logMissingTeam(output.format)
                val teamName = resolvedTeamName ?: queryUseCase.getTeam(id)?.shortName ?: "?"
                val matches = queryUseCase.getLastMatchesByTeam(league, season, id, limit)
                if (matches.isEmpty()) return null
                val teams = resolveTeams(matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatTeamLastMatches(matches, teams, teamName, leagueName, now)
            }

            "team_next_matches" -> {
                val id = teamId ?: return logMissingTeam(output.format)
                val teamName = resolvedTeamName ?: queryUseCase.getTeam(id)?.shortName ?: "?"
                val matches = queryUseCase.getNextMatchesByTeam(league, season, id, limit)
                if (matches.isEmpty()) return null
                val teams = resolveTeams(matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatTeamNextMatches(matches, teams, teamName, leagueName, now)
            }

            "team_top_scorers" -> {
                val id = teamId ?: return logMissingTeam(output.format)
                val teamName = resolvedTeamName ?: queryUseCase.getTeam(id)?.shortName ?: "?"
                val goalGetters = queryUseCase.getGoalGetters(league, season)
                val teams = resolveTeams(goalGetters.map { it.teamId })
                DiscordSender.formatTeamTopScorers(goalGetters, teams, id, teamName, leagueName, now)
            }

            "league_top_scorers" -> {
                val goalGetters = queryUseCase.getGoalGetters(league, season)
                if (goalGetters.isEmpty()) return null
                val teams = resolveTeams(goalGetters.map { it.teamId })
                DiscordSender.formatLeagueTopScorers(goalGetters, teams, leagueName, now, limit)
            }

            "next_matchday" -> {
                val matchday = queryUseCase.getNextMatchday(league, season)
                val matches = queryUseCase.getMatchday(league, season, matchday)
                if (matches.isEmpty()) return null
                val teams = resolveTeams(matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatNextMatchday(matches, teams, matchday, leagueName, now)
            }

            "team_summary" -> {
                val id = teamId ?: return logMissingTeam(output.format)
                val teamName = resolvedTeamName ?: queryUseCase.getTeam(id)?.shortName ?: "?"
                val standings = queryUseCase.getStandings(league, season)
                val standing = standings.firstOrNull { it.teamId == id }
                val lastMatches = queryUseCase.getLastMatchesByTeam(league, season, id, 5)
                val nextMatches = queryUseCase.getNextMatchesByTeam(league, season, id, 1)
                val allTeamIds = (lastMatches + nextMatches)
                    .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
                val teams = resolveTeams(allTeamIds)
                DiscordSender.formatTeamSummary(
                    standing, lastMatches, nextMatches.firstOrNull(),
                    teams, teamName, leagueName, now
                )
            }

            "weekend_summary" -> {
                val matchday = queryUseCase.getCurrentMatchday(league, season)
                val matches = queryUseCase.getMatchdayResults(league, season, matchday)
                if (matches.isEmpty()) return null
                val teams = resolveTeams(matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatWeekendSummary(matches, teams, matchday, leagueName, now)
            }

            "matchday_preview" -> {
                val matchday = queryUseCase.getNextMatchday(league, season)
                val matches = queryUseCase.getMatchday(league, season, matchday)
                if (matches.isEmpty()) return null
                val standings = queryUseCase.getStandings(league, season)
                val standingsMap = standings.associateBy { it.teamId }
                val teams = resolveTeams(matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatMatchdayPreview(matches, teams, standingsMap, matchday, leagueName, now)
            }

            else -> {
                log.warn { "Unbekanntes Ausgabeformat: ${output.format}" }
                null
            }
        }
    }

    private fun resolveTeams(ids: List<Int>): Map<Int, Team> =
        ids.distinct().mapNotNull { queryUseCase.getTeam(it) }.associateBy { it.id }

    private fun logMissingTeam(format: String): String? {
        log.warn { "'$format': Kein teamId in params oder teams-Liste angegeben." }
        return null
    }

    // ── Schedule-Parser ───────────────────────────────────────────────────────

    private fun millisUntilNext(schedule: String): Long {
        val now = LocalDateTime.now()
        val target: LocalDateTime = when {
            schedule.startsWith("DAILY_") -> {
                val parts = schedule.removePrefix("DAILY_").split("_")
                val time = LocalTime.of(parts[0].toInt(), parts[1].toInt())
                val today = LocalDateTime.of(LocalDate.now(), time)
                if (today.isAfter(now)) today else today.plusDays(1)
            }
            schedule.startsWith("WEEKLY_") -> {
                val parts = schedule.removePrefix("WEEKLY_").split("_")
                val day = DayOfWeek.valueOf(parts[0].uppercase())
                val time = LocalTime.of(parts[1].toInt(), parts[2].toInt())
                var date = LocalDate.now().with(day)
                val candidate = LocalDateTime.of(date, time)
                if (!candidate.isAfter(now)) date = date.plusWeeks(1)
                LocalDateTime.of(date, time)
            }
            else -> now.plusMinutes(60)
        }
        return java.time.Duration.between(now, target).toMillis().coerceAtLeast(0)
    }
}
