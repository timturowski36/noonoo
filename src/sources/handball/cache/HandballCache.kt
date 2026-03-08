package sources.handball.cache

import sources.handball.model.HandballMatch
import sources.handball.model.HandballScheduleData
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Cache-Manager für Handball-Spielplandaten.
 *
 * Speichert Spiele mit Zeitstempel und invalidiert automatisch
 * wenn ein neues Spiel stattgefunden haben sollte.
 */
class HandballCache(
    private val cacheDir: String = "data/handball"
) {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val cacheFile get() = File(cacheDir, "schedule.cache")
    private val metaFile get() = File(cacheDir, "schedule.meta")

    init {
        File(cacheDir).mkdirs()
    }

    /**
     * Speichert den Spielplan im Cache.
     */
    fun save(data: HandballScheduleData) {
        val cacheContent = buildString {
            appendLine("# Handball Schedule Cache")
            appendLine("# Generated: ${data.fetchedAt.format(dateTimeFormatter)}")
            appendLine()
            appendLine("TEAM_ID=${data.teamId}")
            appendLine("TEAM_NAME=${data.teamName}")
            appendLine("SEASON=${data.season}")
            appendLine("FETCHED_AT=${data.fetchedAt.format(dateTimeFormatter)}")
            appendLine()
            appendLine("# Matches (ID|DATE|HOME|AWAY|VENUE|SCORE_HOME|SCORE_AWAY|PLAYED)")
            data.matches.forEach { match ->
                appendLine(serializeMatch(match))
            }
        }

        cacheFile.writeText(cacheContent)

        // Meta-Datei mit nächstem Spiel für schnelle Validierung
        val nextMatch = data.nextMatch()
        val metaContent = buildString {
            appendLine("FETCHED_AT=${data.fetchedAt.format(dateTimeFormatter)}")
            if (nextMatch != null) {
                appendLine("NEXT_MATCH_DATE=${nextMatch.date.format(dateTimeFormatter)}")
                appendLine("NEXT_MATCH_ID=${nextMatch.id}")
            }
        }
        metaFile.writeText(metaContent)

        println("💾 [Cache] Spielplan gespeichert (${data.matches.size} Spiele)")
    }

    /**
     * Lädt den Spielplan aus dem Cache.
     */
    fun load(): HandballScheduleData? {
        if (!cacheFile.exists()) {
            println("📂 [Cache] Kein Cache vorhanden")
            return null
        }

        return try {
            val lines = cacheFile.readLines()
            var teamId = ""
            var teamName = ""
            var season = ""
            var fetchedAt = LocalDateTime.now()
            val matches = mutableListOf<HandballMatch>()

            lines.forEach { line ->
                when {
                    line.startsWith("TEAM_ID=") -> teamId = line.substringAfter("=")
                    line.startsWith("TEAM_NAME=") -> teamName = line.substringAfter("=")
                    line.startsWith("SEASON=") -> season = line.substringAfter("=")
                    line.startsWith("FETCHED_AT=") -> {
                        fetchedAt = LocalDateTime.parse(line.substringAfter("="), dateTimeFormatter)
                    }
                    line.startsWith("match_") || line.startsWith("game_") -> {
                        deserializeMatch(line)?.let { matches.add(it) }
                    }
                }
            }

            println("📂 [Cache] Spielplan geladen (${matches.size} Spiele)")

            HandballScheduleData(
                teamId = teamId,
                teamName = teamName,
                season = season,
                matches = matches,
                fetchedAt = fetchedAt
            )
        } catch (e: Exception) {
            println("❌ [Cache] Fehler beim Laden: ${e.message}")
            null
        }
    }

    /**
     * Prüft ob der Cache noch gültig ist.
     *
     * Der Cache ist ungültig wenn:
     * - Kein Cache existiert
     * - Das nächste Spiel in der Vergangenheit liegt (Ergebnis fehlt)
     * - Cache älter als maxAge ist
     */
    fun isValid(maxAgeHours: Int = 24): Boolean {
        if (!metaFile.exists()) return false

        return try {
            val meta = metaFile.readLines().associate {
                val parts = it.split("=", limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }

            val fetchedAt = meta["FETCHED_AT"]?.let {
                LocalDateTime.parse(it, dateTimeFormatter)
            } ?: return false

            val nextMatchDate = meta["NEXT_MATCH_DATE"]?.let {
                LocalDateTime.parse(it, dateTimeFormatter)
            }

            val now = LocalDateTime.now()

            // Cache zu alt?
            if (fetchedAt.plusHours(maxAgeHours.toLong()).isBefore(now)) {
                println("⏰ [Cache] Cache abgelaufen (älter als ${maxAgeHours}h)")
                return false
            }

            // Nächstes Spiel sollte gespielt sein?
            if (nextMatchDate != null && nextMatchDate.plusHours(3).isBefore(now)) {
                println("🏆 [Cache] Neues Spiel sollte beendet sein - Cache ungültig")
                return false
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Prüft ob ein neues Spiel stattgefunden haben sollte.
     */
    fun shouldRefreshForNewResult(): Boolean {
        if (!metaFile.exists()) return true

        return try {
            val meta = metaFile.readLines().associate {
                val parts = it.split("=", limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }

            val nextMatchDate = meta["NEXT_MATCH_DATE"]?.let {
                LocalDateTime.parse(it, dateTimeFormatter)
            } ?: return false

            // Spiel sollte vor 2-3 Stunden beendet sein
            val now = LocalDateTime.now()
            nextMatchDate.plusHours(3).isBefore(now)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Gibt den Zeitpunkt des nächsten Spiels zurück.
     */
    fun getNextMatchDate(): LocalDateTime? {
        if (!metaFile.exists()) return null

        return try {
            val meta = metaFile.readLines().associate {
                val parts = it.split("=", limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }

            meta["NEXT_MATCH_DATE"]?.let {
                LocalDateTime.parse(it, dateTimeFormatter)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Löscht den Cache.
     */
    fun clear() {
        cacheFile.delete()
        metaFile.delete()
        println("🗑️ [Cache] Cache gelöscht")
    }

    private fun serializeMatch(match: HandballMatch): String {
        return listOf(
            match.id,
            match.date.format(dateTimeFormatter),
            match.homeTeam,
            match.awayTeam,
            match.venue ?: "",
            match.scoreHome?.toString() ?: "",
            match.scoreAway?.toString() ?: "",
            match.isPlayed.toString()
        ).joinToString("|")
    }

    private fun deserializeMatch(line: String): HandballMatch? {
        val parts = line.split("|")
        if (parts.size < 8) return null

        return try {
            HandballMatch(
                id = parts[0],
                date = LocalDateTime.parse(parts[1], dateTimeFormatter),
                homeTeam = parts[2],
                awayTeam = parts[3],
                venue = parts[4].ifEmpty { null },
                scoreHome = parts[5].toIntOrNull(),
                scoreAway = parts[6].toIntOrNull(),
                isPlayed = parts[7].toBoolean()
            )
        } catch (e: Exception) {
            null
        }
    }
}
