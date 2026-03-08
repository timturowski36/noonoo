package sources.handball.cache

import sources.handball.model.HandballTableData
import sources.handball.model.HandballTableEntry
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Cache für Handball-Tabellendaten.
 *
 * Invalidierungs-Logik:
 * - Neu laden wenn lastMatchTimestamp neuer als cachedTimestamp
 * - Neu laden jeden Sonntag um 22:00 Uhr
 * - Ansonsten Cache verwenden
 */
class HandballTableCache(
    private val cacheDir: String = "data/handball"
) {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val cacheFile get() = File(cacheDir, "table.cache")
    private val metaFile get() = File(cacheDir, "table.meta")

    init {
        File(cacheDir).mkdirs()
    }

    /**
     * Speichert die Tabelle im Cache.
     */
    fun save(data: HandballTableData) {
        val cacheContent = buildString {
            appendLine("# Handball Table Cache")
            appendLine("# Generated: ${data.fetchedAt.format(dateTimeFormatter)}")
            appendLine()
            appendLine("TEAM_ID=${data.teamId}")
            appendLine("LEAGUE=${data.league}")
            appendLine("SEASON=${data.season}")
            appendLine("FETCHED_AT=${data.fetchedAt.format(dateTimeFormatter)}")
            appendLine()
            appendLine("# Entries (POS|TEAM|GAMES|W|D|L|GF|GA|DIFF|PTS)")
            data.entries.forEach { entry ->
                appendLine(serializeEntry(entry))
            }
        }

        cacheFile.writeText(cacheContent)
        metaFile.writeText("FETCHED_AT=${data.fetchedAt.format(dateTimeFormatter)}")

        println("💾 [TableCache] Tabelle gespeichert (${data.entries.size} Teams)")
    }

    /**
     * Lädt die Tabelle aus dem Cache.
     */
    fun load(): HandballTableData? {
        if (!cacheFile.exists()) {
            println("📂 [TableCache] Kein Cache vorhanden")
            return null
        }

        return try {
            val lines = cacheFile.readLines()
            var teamId = ""
            var league = ""
            var season = ""
            var fetchedAt = LocalDateTime.now()
            val entries = mutableListOf<HandballTableEntry>()

            lines.forEach { line ->
                when {
                    line.startsWith("TEAM_ID=") -> teamId = line.substringAfter("=")
                    line.startsWith("LEAGUE=") -> league = line.substringAfter("=")
                    line.startsWith("SEASON=") -> season = line.substringAfter("=")
                    line.startsWith("FETCHED_AT=") -> {
                        fetchedAt = LocalDateTime.parse(line.substringAfter("="), dateTimeFormatter)
                    }
                    line.contains("|") && !line.startsWith("#") -> {
                        deserializeEntry(line)?.let { entries.add(it) }
                    }
                }
            }

            println("📂 [TableCache] Tabelle geladen (${entries.size} Teams)")

            HandballTableData(
                teamId = teamId,
                league = league,
                season = season,
                entries = entries.sortedBy { it.position },
                fetchedAt = fetchedAt
            )
        } catch (e: Exception) {
            println("❌ [TableCache] Fehler beim Laden: ${e.message}")
            null
        }
    }

    /**
     * Prüft ob der Cache noch gültig ist.
     *
     * @param lastMatchTimestamp Zeitstempel des letzten Spiels (null = kein neues Spiel)
     * @return true wenn Cache gültig, false wenn neu geladen werden soll
     */
    fun isValid(lastMatchTimestamp: LocalDateTime? = null): Boolean {
        if (!metaFile.exists()) return false

        return try {
            val meta = metaFile.readText()
            val fetchedAtStr = meta.substringAfter("FETCHED_AT=").trim()
            val fetchedAt = LocalDateTime.parse(fetchedAtStr, dateTimeFormatter)

            val now = LocalDateTime.now()

            // 1. Prüfe ob Sonntag 22:00 überschritten wurde seit letztem Fetch
            if (shouldRefreshForSunday(fetchedAt, now)) {
                println("📅 [TableCache] Sonntag 22:00 erreicht - Cache ungültig")
                return false
            }

            // 2. Prüfe ob neues Spiel stattgefunden hat
            if (lastMatchTimestamp != null && lastMatchTimestamp.isAfter(fetchedAt)) {
                println("🏆 [TableCache] Neues Spiel seit Cache - ungültig")
                return false
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Prüft ob ein Sonntag 22:00 zwischen fetchedAt und now liegt.
     */
    private fun shouldRefreshForSunday(fetchedAt: LocalDateTime, now: LocalDateTime): Boolean {
        // Finde den letzten Sonntag 22:00
        var sunday = now.with(DayOfWeek.SUNDAY).withHour(22).withMinute(0).withSecond(0)

        // Wenn heute Sonntag ist aber vor 22:00, gehe eine Woche zurück
        if (now.dayOfWeek == DayOfWeek.SUNDAY && now.hour < 22) {
            sunday = sunday.minusWeeks(1)
        }
        // Wenn nach Sonntag, nimm diesen Sonntag
        else if (now.dayOfWeek != DayOfWeek.SUNDAY) {
            // Finde den letzten Sonntag
            val daysToSubtract = (now.dayOfWeek.value % 7).toLong()
            sunday = now.minusDays(daysToSubtract).withHour(22).withMinute(0).withSecond(0)
        }

        // Cache ungültig wenn Sonntag 22:00 nach dem Fetch-Zeitpunkt liegt
        return sunday.isAfter(fetchedAt) && sunday.isBefore(now)
    }

    /**
     * Löscht den Cache.
     */
    fun clear() {
        cacheFile.delete()
        metaFile.delete()
        println("🗑️ [TableCache] Cache gelöscht")
    }

    private fun serializeEntry(entry: HandballTableEntry): String {
        return listOf(
            entry.position.toString(),
            entry.teamName,
            entry.games.toString(),
            entry.wins.toString(),
            entry.draws.toString(),
            entry.losses.toString(),
            entry.goalsFor.toString(),
            entry.goalsAgainst.toString(),
            entry.goalDiff.toString(),
            entry.points.toString()
        ).joinToString("|")
    }

    private fun deserializeEntry(line: String): HandballTableEntry? {
        val parts = line.split("|")
        if (parts.size < 10) return null

        return try {
            HandballTableEntry(
                position = parts[0].toInt(),
                teamName = parts[1],
                games = parts[2].toInt(),
                wins = parts[3].toInt(),
                draws = parts[4].toInt(),
                losses = parts[5].toInt(),
                goalsFor = parts[6].toInt(),
                goalsAgainst = parts[7].toInt(),
                goalDiff = parts[8].toInt(),
                points = parts[9].toInt()
            )
        } catch (e: Exception) {
            null
        }
    }
}
