package sources.pubg.history

import sources.pubg.model.PlayerStats
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Speichert PUBG Stats-Snapshots als einzelne JSON-Dateien in data/pubg/.
 *
 * Pro Aufruf (= ein Stats-Versand) wird eine Datei angelegt:
 *   data/pubg/2026-03-14_22-30_brotrustgaming.json
 *
 * Die Datei enthält daily + weekly Stats als strukturiertes JSON-Objekt.
 */
object PubgStatsHistory {

    private val outputDir = File("data/pubg")
    private val filenameDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
    private val isoFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val berlin = ZoneId.of("Europe/Berlin")

    /**
     * Speichert einen Stats-Snapshot für einen Spieler als JSON-Datei.
     *
     * @param playerName  PUBG-Spielername
     * @param platform    z.B. "steam"
     * @param dailyStats  Stats der letzten 24h (oder null)
     * @param weeklyStats Stats seit Montag 06:00 (oder null)
     */
    fun save(
        playerName: String,
        platform: String,
        dailyStats: PlayerStats?,
        weeklyStats: PlayerStats?
    ) {
        if (dailyStats == null && weeklyStats == null) return

        outputDir.mkdirs()

        val now = ZonedDateTime.now(berlin)
        val timestamp = now.format(isoFormat)
        val filePrefix = now.format(filenameDateFormat)
        val file = File(outputDir, "${filePrefix}_${playerName}.json")

        val json = buildString {
            appendLine("{")
            appendLine("  \"timestamp\": \"$timestamp\",")
            appendLine("  \"player\": \"${playerName.escape()}\",")
            appendLine("  \"platform\": \"${platform.escape()}\",")

            if (dailyStats != null && dailyStats.matches > 0) {
                appendLine("  \"daily\": ${dailyStats.toJson()},")
            } else {
                appendLine("  \"daily\": null,")
            }

            if (weeklyStats != null && weeklyStats.matches > 0) {
                append("  \"weekly\": ${weeklyStats.toJson()}")
            } else {
                append("  \"weekly\": null")
            }

            appendLine()
            append("}")
        }

        file.writeText(json)
        println("   History gespeichert: ${file.path}")
    }

    private fun PlayerStats.toJson(): String = buildString {
        appendLine("{")
        appendLine("    \"matches\": $matches,")
        appendLine("    \"wins\": $wins,")
        appendLine("    \"kills\": $kills,")
        appendLine("    \"deaths\": $deaths,")
        appendLine("    \"kd\": ${"%.2f".format(kd)},")
        appendLine("    \"damageDealt\": ${"%.2f".format(damageDealt)},")
        appendLine("    \"avgDamage\": ${"%.2f".format(avgDamage)},")
        appendLine("    \"assists\": $assists,")
        appendLine("    \"headshotKills\": $headshotKills,")
        appendLine("    \"headshotRate\": ${"%.2f".format(headshotRate)},")
        appendLine("    \"topTens\": $topTens,")
        appendLine("    \"topTenRate\": ${"%.2f".format(topTenRate)},")
        appendLine("    \"revives\": $revives,")
        appendLine("    \"knockdowns\": $knockdowns,")
        append("    \"longestKill\": ${"%.2f".format(longestKill)}")
        appendLine()
        append("  }")
    }

    private fun String.escape() = replace("\\", "\\\\").replace("\"", "\\\"")
}
