package de.aggregator.adapter.input.scheduler

import de.aggregator.adapter.config.ModuleConfig
import de.aggregator.adapter.config.OutputConfig
import de.aggregator.adapter.output.discord.DiscordSender
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
                        val message = buildMessage(output, league, season)
                        if (message != null) {
                            notificationPort.send(output.channel, message)
                            log.info { "[${module.id}] Ausgabe '${output.format}' gesendet → #${output.channel}." }
                        }
                    } catch (e: Exception) {
                        log.error(e) { "[${module.id}] Fehler bei Ausgabe '${output.format}': ${e.message}" }
                    }
                }
            }
        }
    }

    private fun buildMessage(output: OutputConfig, league: String, season: Int): String? {
        val teamIds = queryUseCase.getStandings(league, season).map { it.teamId } +
            queryUseCase.getMatchdayResults(league, season, queryUseCase.getCurrentMatchday(league, season))
                .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
        val teams = teamIds.distinct().mapNotNull { queryUseCase.getTeam(it) }.associateBy { it.id }

        return when (output.format) {
            "table_summary" -> {
                val standings = queryUseCase.getStandings(league, season)
                if (standings.isEmpty()) null
                else DiscordSender.formatTableSummary(
                    standings, teams, league,
                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                )
            }
            "matchday_results" -> {
                val matchday = queryUseCase.getCurrentMatchday(league, season)
                val matches = queryUseCase.getMatchdayResults(league, season, matchday)
                if (matches.isEmpty()) null
                else DiscordSender.formatMatchdayResults(matches, teams, matchday)
            }
            else -> {
                log.warn { "Unbekanntes Ausgabeformat: ${output.format}" }
                null
            }
        }
    }

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
                val target = LocalDateTime.of(date, time)
                if (!target.isAfter(now)) date = date.plusWeeks(1)
                LocalDateTime.of(date, time)
            }
            else -> now.plusMinutes(60)
        }
        return java.time.Duration.between(now, target).toMillis().coerceAtLeast(0)
    }
}
