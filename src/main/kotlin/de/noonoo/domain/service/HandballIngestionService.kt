package de.noonoo.domain.service

import de.noonoo.domain.port.input.FetchHandballDataUseCase
import de.noonoo.domain.port.output.HandballApiPort
import de.noonoo.domain.port.output.HandballRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

class HandballIngestionService(
    private val apiPort: HandballApiPort,
    private val repository: HandballRepository
) : FetchHandballDataUseCase {

    override suspend fun fetchAndStore() {
        val matches = apiPort.fetchTeamSchedule()
        if (matches.isEmpty()) {
            log.warn { "[Handball] Kein Spielplan empfangen." }
            return
        }
        repository.saveMatches(matches)
        log.info { "[Handball] ${matches.size} Spiele gespeichert." }

        val leagueId = matches.first().leagueId
        val standings = apiPort.fetchLeagueTable(leagueId)
        repository.saveStandings(standings)
        log.info { "[Handball] ${standings.size} Tabelleneinträge gespeichert." }

        val now = LocalDateTime.now()
        val startedMatches = matches.filter { match ->
            try {
                val kickoff = parseKickoff(match.kickoffDate, match.kickoffTime)
                kickoff.isBefore(now)
            } catch (e: Exception) {
                false
            }
        }

        log.info { "[Handball] Starte Ticker-Abruf für ${startedMatches.size} gestartete Spiele..." }
        for (match in startedMatches) {
            try {
                val events = apiPort.fetchMatchTicker(match.id)
                if (events.isNotEmpty()) {
                    repository.saveTickerEvents(events)
                    log.info { "[Handball] Spiel ${match.id}: ${events.size} Ticker-Events gespeichert." }
                }
            } catch (e: Exception) {
                log.warn { "[Handball] Ticker-Abruf für Spiel ${match.id} fehlgeschlagen: ${e.message}" }
            }
        }
    }

    private fun parseKickoff(date: String, time: String): LocalDateTime {
        // API format: "10.04.26" and "14:00"
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
        return LocalDateTime.parse("$date $time", formatter)
    }
}
