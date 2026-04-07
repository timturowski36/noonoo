package de.noonoo.adapter.input.scheduler

import de.noonoo.adapter.config.ModuleConfig
import de.noonoo.adapter.config.OutputConfig
import de.noonoo.adapter.output.discord.DiscordSender
import de.noonoo.adapter.output.discord.PubgDiscordFormatter
import de.noonoo.domain.model.Team
import de.noonoo.domain.port.input.FetchDataUseCase
import de.noonoo.domain.port.input.FetchNewsUseCase
import de.noonoo.domain.port.input.FetchPubgDataUseCase
import de.noonoo.domain.port.input.QueryDataUseCase
import de.noonoo.domain.port.input.QueryPubgDataUseCase
import de.noonoo.domain.port.output.NewsRepository
import de.noonoo.domain.port.output.NotificationPort
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
    private val fetchPubgUseCase: FetchPubgDataUseCase,
    private val queryPubgUseCase: QueryPubgDataUseCase,
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
                "pubg"     -> startPubgIngestion(module)
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

    private fun startPubgIngestion(module: ModuleConfig) {
        val platform = module.config["platform"] ?: run {
            log.warn { "[${module.id}] Kein 'platform' in module.config – Ingestion übersprungen." }
            return
        }
        val players = module.players ?: run {
            log.warn { "[${module.id}] Keine 'players' definiert – Ingestion übersprungen." }
            return
        }
        val intervalMs = module.schedule.fetchIntervalMinutes * 60_000L

        scope.launch {
            while (isActive) {
                try {
                    log.info { "[${module.id}] Starte PUBG-Abruf ($platform, ${players.size} Spieler)..." }
                    fetchPubgUseCase.fetchAndStore(players, platform)
                    log.info { "[${module.id}] PUBG-Abruf abgeschlossen." }
                } catch (e: Exception) {
                    log.error(e) { "[${module.id}] Fehler beim PUBG-Abruf: ${e.message}" }
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
                            "pubg"     -> sendPubgOutput(module, output)
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

    // ── PUBG Output ───────────────────────────────────────────────────────────

    private suspend fun sendPubgOutput(module: ModuleConfig, output: OutputConfig) {
        val platform = module.config["platform"] ?: return
        val playerNames = module.players ?: return
        val limit = output.params?.get("limit")?.toIntOrNull() ?: 5

        val today = java.time.LocalDate.now()
        val startOfDay = today.atStartOfDay()
        val endOfDay = today.plusDays(1).atStartOfDay()
        val startOfWeek = today.with(java.time.DayOfWeek.MONDAY).atStartOfDay()
        val startOfLastWeek = startOfWeek.minusWeeks(1)

        when (output.format) {
            "pubg_daily_stats" -> {
                playerNames.forEach { name ->
                    val player = queryPubgUseCase.getPlayerByName(name) ?: run {
                        log.warn { "[${module.id}] Spieler '$name' nicht in DB." }; return@forEach
                    }
                    val stats = queryPubgUseCase.getPeriodStats(player.accountId, startOfDay, endOfDay)
                    val msg = PubgDiscordFormatter.formatDailyStats(player.name, platform, stats)
                    notificationPort.send(output.channel, msg)
                    delay(500)
                }
            }
            "pubg_weekly_stats" -> {
                playerNames.forEach { name ->
                    val player = queryPubgUseCase.getPlayerByName(name) ?: run {
                        log.warn { "[${module.id}] Spieler '$name' nicht in DB." }; return@forEach
                    }
                    val stats = queryPubgUseCase.getPeriodStats(player.accountId, startOfWeek, endOfDay)
                    val msg = PubgDiscordFormatter.formatWeeklyStats(player.name, platform, stats)
                    notificationPort.send(output.channel, msg)
                    delay(500)
                }
            }
            "pubg_records" -> {
                playerNames.forEach { name ->
                    val player = queryPubgUseCase.getPlayerByName(name) ?: run {
                        log.warn { "[${module.id}] Spieler '$name' nicht in DB." }; return@forEach
                    }
                    val records = queryPubgUseCase.getRecords(player.accountId)
                    val msg = PubgDiscordFormatter.formatRecords(player.name, records)
                    notificationPort.send(output.channel, msg)
                    delay(500)
                }
            }
            "pubg_recent_matches" -> {
                playerNames.forEach { name ->
                    val player = queryPubgUseCase.getPlayerByName(name) ?: run {
                        log.warn { "[${module.id}] Spieler '$name' nicht in DB." }; return@forEach
                    }
                    val matches = queryPubgUseCase.getRecentMatches(player.accountId, limit)
                    val msg = PubgDiscordFormatter.formatRecentMatches(player.name, limit, matches)
                    notificationPort.send(output.channel, msg)
                    delay(500)
                }
            }
            "pubg_map_stats" -> {
                playerNames.forEach { name ->
                    val player = queryPubgUseCase.getPlayerByName(name) ?: run {
                        log.warn { "[${module.id}] Spieler '$name' nicht in DB." }; return@forEach
                    }
                    val stats = queryPubgUseCase.getMapStats(player.accountId)
                    val msg = PubgDiscordFormatter.formatMapStats(player.name, stats)
                    notificationPort.send(output.channel, msg)
                    delay(500)
                }
            }
            "pubg_mode_compare" -> {
                playerNames.forEach { name ->
                    val player = queryPubgUseCase.getPlayerByName(name) ?: run {
                        log.warn { "[${module.id}] Spieler '$name' nicht in DB." }; return@forEach
                    }
                    val stats = queryPubgUseCase.getLifetimeStatsByMode(player.accountId)
                    val msg = PubgDiscordFormatter.formatModeCompare(player.name, stats)
                    notificationPort.send(output.channel, msg)
                    delay(500)
                }
            }
            "pubg_weekly_ranking" -> {
                val entries = playerNames.mapNotNull { name ->
                    val player = queryPubgUseCase.getPlayerByName(name) ?: return@mapNotNull null
                    val stats = queryPubgUseCase.getPeriodStats(player.accountId, startOfWeek, endOfDay)
                    if (stats.matches == 0) return@mapNotNull null
                    player.name to stats
                }
                if (entries.isNotEmpty()) {
                    val msg = PubgDiscordFormatter.formatWeeklyRanking(entries)
                    notificationPort.send(output.channel, msg)
                }
            }
            "pubg_weekly_progress" -> {
                playerNames.forEach { name ->
                    val player = queryPubgUseCase.getPlayerByName(name) ?: run {
                        log.warn { "[${module.id}] Spieler '$name' nicht in DB." }; return@forEach
                    }
                    val lastWeek = queryPubgUseCase.getPeriodStats(player.accountId, startOfLastWeek, startOfWeek)
                    val thisWeek = queryPubgUseCase.getPeriodStats(player.accountId, startOfWeek, endOfDay)
                    val msg = PubgDiscordFormatter.formatWeeklyProgress(player.name, lastWeek, thisWeek)
                    notificationPort.send(output.channel, msg)
                    delay(500)
                }
            }
            else -> log.warn { "[${module.id}] Unbekanntes PUBG-Format: ${output.format}" }
        }
        log.info { "[${module.id}] '${output.format}' → #${output.channel}." }
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
