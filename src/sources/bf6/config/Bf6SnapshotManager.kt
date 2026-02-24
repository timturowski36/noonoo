package sources.bf6.config

import sources.bf6.model.Bf6Stats
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class Bf6SnapshotManager {
    private val configDir = File("src/sources/bf6/config")
    private val zone = ZoneId.of("Europe/Berlin")
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // ─────────────────────────────────────────────────────────────────────────
    // Startzeitpunkt der aktuellen Woche (Mo. 06:00 Uhr)
    // ─────────────────────────────────────────────────────────────────────────

    private fun currentWeekStart(): LocalDateTime {
        val now = LocalDateTime.now(zone)
        val lastMonday = if (now.dayOfWeek == DayOfWeek.MONDAY && now.hour >= 6) {
            now.toLocalDate()
        } else {
            now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        return lastMonday.atTime(6, 0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prüft ob der gespeicherte Snapshot aus einer vergangenen Woche stammt
    // ─────────────────────────────────────────────────────────────────────────

    fun isBaselineStale(playerName: String): Boolean {
        val file = baselineFile(playerName)
        if (!file.exists()) return true

        val savedAt = file.readLines()
            .firstOrNull { it.startsWith("savedAt=") }
            ?.substringAfter("=")
            ?.let { runCatching { LocalDateTime.parse(it, formatter) }.getOrNull() }
            ?: return true

        return savedAt.isBefore(currentWeekStart())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wöchentliche Basislinie laden
    // ─────────────────────────────────────────────────────────────────────────

    fun loadBaseline(playerName: String): Bf6Stats? {
        val file = baselineFile(playerName)
        if (!file.exists()) return null

        return runCatching {
            val props = file.readLines()
                .filter { it.contains("=") }
                .associate { it.substringBefore("=") to it.substringAfter("=") }

            Bf6Stats(
                userName        = props["userName"] ?: playerName,
                kills           = props["kills"]?.toIntOrNull() ?: 0,
                deaths          = props["deaths"]?.toIntOrNull() ?: 0,
                killDeath       = props["killDeath"]?.toDoubleOrNull() ?: 0.0,
                wins            = props["wins"]?.toIntOrNull() ?: 0,
                losses          = props["losses"]?.toIntOrNull() ?: 0,
                matchesPlayed   = props["matchesPlayed"]?.toIntOrNull() ?: 0,
                winPercent      = props["winPercent"] ?: "-",
                killAssists     = props["killAssists"]?.toIntOrNull() ?: 0,
                headShots       = props["headShots"]?.toIntOrNull() ?: 0,
                headshotPercent = props["headshotPercent"] ?: "-",
                revives         = props["revives"]?.toIntOrNull() ?: 0,
                accuracy        = props["accuracy"] ?: "-",
                timePlayed      = props["timePlayed"] ?: "-",
                killsPerMatch   = props["killsPerMatch"]?.toDoubleOrNull() ?: 0.0
            )
        }.getOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Aktuelle Stats als neue Wochenbasislinie speichern
    // ─────────────────────────────────────────────────────────────────────────

    fun saveBaseline(playerName: String, stats: Bf6Stats) {
        configDir.mkdirs()
        baselineFile(playerName).writeText(buildString {
            appendLine("savedAt=${LocalDateTime.now(zone).format(formatter)}")
            appendLine("userName=${stats.userName}")
            appendLine("kills=${stats.kills}")
            appendLine("deaths=${stats.deaths}")
            appendLine("killDeath=${stats.killDeath}")
            appendLine("wins=${stats.wins}")
            appendLine("losses=${stats.losses}")
            appendLine("matchesPlayed=${stats.matchesPlayed}")
            appendLine("winPercent=${stats.winPercent}")
            appendLine("killAssists=${stats.killAssists}")
            appendLine("headShots=${stats.headShots}")
            appendLine("headshotPercent=${stats.headshotPercent}")
            appendLine("revives=${stats.revives}")
            appendLine("accuracy=${stats.accuracy}")
            appendLine("timePlayed=${stats.timePlayed}")
            append("killsPerMatch=${stats.killsPerMatch}")
        })
        println("💾 [BF6] Wochenbasislinie für $playerName gespeichert.")
    }

    private fun baselineFile(playerName: String) =
        File(configDir, "${playerName}_weekly_baseline.txt")
}
