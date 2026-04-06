package de.feedkrake.adapter.input.scheduler

import de.feedkrake.adapter.config.ModuleConfig
import de.feedkrake.adapter.config.OutputConfig
import de.feedkrake.adapter.output.discord.DiscordSender
import de.feedkrake.domain.model.Team
import de.feedkrake.domain.port.input.FetchDataUseCase
import de.feedkrake.domain.port.input.FetchNewsUseCase
import de.feedkrake.domain.port.input.QueryDataUseCase
import de.feedkrake.domain.port.output.NewsRepository
import de.feedkrake.domain.port.output.NotificationPort
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
    private val fetchNewsUseCase: FetchNewsUseCase,
    private val newsRepository: NewsRepository,
    private val notificationPort: NotificationPort,
    private val webhookChannels: Map<String, String>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        modules.filter { it.enabled }.forEach { module ->
            when (module.type) {
                "football" -> startFootballIngestion(module)
                "news"     -> startNewsIngestion(module)
                else       -> log.warn { "[${module.id}] Unbekannter Modultyp: ${module.type}" }
            }
            startOutputSchedules(module)
        }
    }

    fun stop() {
        scope.cancel()
    }

    // ── Ingestion ─────────────────────────────────────────────────────────────

    private fun startFootballIngestion(module: ModuleConfig) {
        val league = module.config["league"] ?: return
        val season = module.config["season"]?.toInt() ?: return
        val intervalMs = module.schedule.fetchIntervalMinutes * 60_000L

        scope.launch {
            while (isActive) {
                try {
                    log.info { "[${module.id}] Starte Fußball-Abruf ($league/$season)..." }
                    fetchUseCase.fetchAndStore(league, season)
                    log.info { "[${module.id}] Fußball-Abruf abgeschlossen." }
                } catch (e: Exception) {
                    log.error(e) { "[${module.id}] Fehler beim Fußball-Abruf: ${e.message}" }
                }
                delay(intervalMs)
            }
        }
    }

    private fun startNewsIngestion(module: ModuleConfig) {
        val url = module.config["url"] ?: run {
            log.warn { "[${module.id}] Kein 'url' in module.config – Ingestion übersprungen." }
            return
        }
        val sourceName = module.config["sourceName"] ?: module.id
        val intervalMs = module.schedule.fetchIntervalMinutes * 60_000L

        scope.launch {
            while (isActive) {
                try {
                    log.info { "[${module.id}] Starte News-Abruf ($sourceName)..." }
                    fetchNewsUseCase.fetchAndStoreNews(url, sourceName)
                    log.info { "[${module.id}] News-Abruf abgeschlossen." }
                } catch (e: Exception) {
                    log.error(e) { "[${module.id}] Fehler beim News-Abruf: ${e.message}" }
                }
                delay(intervalMs)
            }
        }
    }

    // ── Output-Schedules ──────────────────────────────────────────────────────

    private fun startOutputSchedules(module: ModuleConfig) {
        module.outputs.forEach { output ->
            scope.launch {
                while (isActive) {
                    val delayMs = millisUntilNext(output.schedule)
                    log.info { "[${module.id}] Nächste Ausgabe '${output.format}' in ${delayMs / 60_000}min." }
                    delay(delayMs)
                    try {
                        when (module.type) {
                            "football" -> sendFootballOutput(module, output)
                            "news"     -> sendNewsOutput(module, output)
                        }
                    } catch (e: Exception) {
                        log.error(e) { "[${module.id}] Fehler bei Ausgabe '${output.format}': ${e.message}" }
                    }
                }
            }
        }
    }

    // ── Football Output ───────────────────────────────────────────────────────

    private suspend fun sendFootballOutput(module: ModuleConfig, output: OutputConfig) {
        val league = module.config["league"] ?: return
        val season = module.config["season"]?.toInt() ?: return

        val teamNames = output.teams
        if (!teamNames.isNullOrEmpty() && isTeamSpecificFormat(output.format)) {
            for (teamName in teamNames) {
                val team = queryUseCase.getTeamByName(teamName)
                if (team == null) {
                    log.warn { "[${module.id}] Team '$teamName' nicht in DB – übersprungen." }
                    continue
                }
                val message = buildFootballMessage(output, league, season, team.id, team.shortName)
                if (message != null) {
                    notificationPort.send(output.channel, message)
                    log.info { "[${module.id}] '${output.format}' für '${team.shortName}' → #${output.channel}." }
                    delay(500)
                }
            }
        } else {
            val message = buildFootballMessage(output, league, season, null, null)
            if (message != null) {
                notificationPort.send(output.channel, message)
                log.info { "[${module.id}] '${output.format}' → #${output.channel}." }
            }
        }
    }

    private fun isTeamSpecificFormat(format: String) = format in setOf(
        "team_last_matches", "team_next_matches", "team_top_scorers", "team_summary"
    )

    private suspend fun buildFootballMessage(
        output: OutputConfig,
        league: String,
        season: Int,
        resolvedTeamId: Int?,
        resolvedTeamName: String?
    ): String? {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        val leagueName = output.params?.get("leagueName") ?: league
        val limit = output.params?.get("limit")?.toIntOrNull() ?: 5
        val teamId = resolvedTeamId ?: output.params?.get("teamId")?.toIntOrNull()

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
                val name = resolvedTeamName ?: queryUseCase.getTeam(id)?.shortName ?: "?"
                val matches = queryUseCase.getLastMatchesByTeam(league, season, id, limit)
                if (matches.isEmpty()) return null
                val teams = resolveTeams(matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatTeamLastMatches(matches, teams, name, leagueName, now)
            }
            "team_next_matches" -> {
                val id = teamId ?: return logMissingTeam(output.format)
                val name = resolvedTeamName ?: queryUseCase.getTeam(id)?.shortName ?: "?"
                val matches = queryUseCase.getNextMatchesByTeam(league, season, id, limit)
                if (matches.isEmpty()) return null
                val teams = resolveTeams(matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatTeamNextMatches(matches, teams, name, leagueName, now)
            }
            "team_top_scorers" -> {
                val id = teamId ?: return logMissingTeam(output.format)
                val name = resolvedTeamName ?: queryUseCase.getTeam(id)?.shortName ?: "?"
                val goalGetters = queryUseCase.getGoalGetters(league, season)
                val teams = resolveTeams(goalGetters.map { it.teamId })
                DiscordSender.formatTeamTopScorers(goalGetters, teams, id, name, leagueName, now)
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
                val name = resolvedTeamName ?: queryUseCase.getTeam(id)?.shortName ?: "?"
                val standings = queryUseCase.getStandings(league, season)
                val standing = standings.firstOrNull { it.teamId == id }
                val lastMatches = queryUseCase.getLastMatchesByTeam(league, season, id, 5)
                val nextMatches = queryUseCase.getNextMatchesByTeam(league, season, id, 1)
                val teams = resolveTeams((lastMatches + nextMatches).flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatTeamSummary(standing, lastMatches, nextMatches.firstOrNull(), teams, name, leagueName, now)
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
                val standingsMap = queryUseCase.getStandings(league, season).associateBy { it.teamId }
                val teams = resolveTeams(matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) })
                DiscordSender.formatMatchdayPreview(matches, teams, standingsMap, matchday, leagueName, now)
            }
            else -> {
                log.warn { "Unbekanntes Fußball-Format: ${output.format}" }
                null
            }
        }
    }

    // ── News Output ───────────────────────────────────────────────────────────

    private suspend fun sendNewsOutput(module: ModuleConfig, output: OutputConfig) {
        val sourceName = module.config["sourceName"] ?: module.id
        val message = buildNewsMessage(output, sourceName)
        if (message != null) {
            notificationPort.send(output.channel, message)
            log.info { "[${module.id}] '${output.format}' → #${output.channel}." }
        }
    }

    private fun buildNewsMessage(output: OutputConfig, sourceName: String): String? {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        val limit = output.params?.get("limit")?.toIntOrNull() ?: 5
        val keywords = output.params?.get("keywords")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: emptyList()

        val articles = if (keywords.isEmpty()) {
            newsRepository.findLatestArticles(sourceName, limit)
        } else {
            newsRepository.findArticlesByKeywords(sourceName, keywords, limit)
        }

        if (articles.isEmpty()) {
            log.info { "Keine News für '$sourceName' (keywords=$keywords)." }
            return null
        }

        return when (output.format) {
            "news_compact" -> DiscordSender.formatNewsCompact(articles, sourceName, now)
            else -> {
                log.warn { "Unbekanntes News-Format: ${output.format}" }
                null
            }
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

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
