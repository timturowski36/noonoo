package sources.pubg.history

import sources.pubg.model.PlayerStats
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Schreibt PUBG Statistiken progressiv als JSON Lines (JSONL) in eine Datei.
 *
 * Format: Eine JSON-Zeile pro Eintrag, ans Ende der Datei angehängt.
 * Datei: data/pubg_history.jsonl
 *
 * Beispiel-Eintrag:
 * {"timestamp":"2026-03-14T22:30:00+01:00","player":"brotrustgaming","platform":"steam","type":"daily","matches":5,...}
 */
object PubgStatsHistory {

    private val historyFile = File("data/pubg_history.jsonl")
    private val timestampFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val berlin = ZoneId.of("Europe/Berlin")

    /**
     * Speichert Daily- und/oder Weekly-Stats eines Spielers.
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
        historyFile.parentFile?.mkdirs()

        val now = ZonedDateTime.now(berlin).format(timestampFormatter)

        historyFile.appendText(
            buildString {
                if (dailyStats != null && dailyStats.matches > 0) {
                    appendLine(toJsonLine(now, playerName, platform, "daily", dailyStats))
                }
                if (weeklyStats != null && weeklyStats.matches > 0) {
                    appendLine(toJsonLine(now, playerName, platform, "weekly", weeklyStats))
                }
            }
        )
    }

    private fun toJsonLine(
        timestamp: String,
        player: String,
        platform: String,
        type: String,
        stats: PlayerStats
    ): String {
        return buildString {
            append("{")
            appendField("timestamp", timestamp)
            append(","); appendField("player", player)
            append(","); appendField("platform", platform)
            append(","); appendField("type", type)
            append(","); appendNum("matches", stats.matches)
            append(","); appendNum("wins", stats.wins)
            append(","); appendNum("kills", stats.kills)
            append(","); appendNum("deaths", stats.deaths)
            append(","); appendDec("kd", stats.kd)
            append(","); appendDec("damageDealt", stats.damageDealt)
            append(","); appendDec("avgDamage", stats.avgDamage)
            append(","); appendNum("assists", stats.assists)
            append(","); appendNum("headshotKills", stats.headshotKills)
            append(","); appendDec("headshotRate", stats.headshotRate)
            append(","); appendNum("topTens", stats.topTens)
            append(","); appendDec("topTenRate", stats.topTenRate)
            append(","); appendNum("revives", stats.revives)
            append(","); appendNum("knockdowns", stats.knockdowns)
            append(","); appendDec("longestKill", stats.longestKill)
            append("}")
        }
    }

    private fun StringBuilder.appendField(key: String, value: String) {
        append("\"$key\":\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    private fun StringBuilder.appendNum(key: String, value: Int) {
        append("\"$key\":$value")
    }

    private fun StringBuilder.appendDec(key: String, value: Double) {
        append("\"$key\":${"%.2f".format(value)}")
    }
}
