package scheduler

import scheduler.discord.DiscordWebhook
import sources.`pubg-api`.api.PubgApiClient
import sources.pubg.model.PlayerStats
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Geplantes Modul mit Zeitversatz.
 *
 * @param module Das Modul
 * @param minuteOffset Wann es laufen soll (0 = zur vollen Stunde, 30 = zur halben Stunde)
 * @param evenHoursOnly Nur zu geraden Stunden (14:00, 16:00, ...)
 * @param oddHoursOnly Nur zu ungeraden Stunden (15:00, 17:00, ...)
 */
data class ScheduledModuleEntry(
    val module: ScheduledModule,
    val minuteOffset: Int,
    val evenHoursOnly: Boolean = false,
    val oddHoursOnly: Boolean = false
)

/**
 * Kombinierter Observer für FeedKrake.
 *
 * Ablauf:
 * 1. Prüft regelmäßig ob jemand PUBG spielt
 * 2. Niemand online -> gar keine Ausgaben (pausiert)
 * 3. Jemand online -> PUBG Stats alle X Minuten senden
 * 4. Jemand online -> Handball-Module zeitversetzt:
 *    - Minute 0: Ergebnisse
 *    - Minute 30: Nächste Spiele
 *    - Minute 0 (nächste Stunde): Tabelle
 */
class CombinedObserver(
    private val pubgPlayers: List<String>,
    private val pubgPlatform: String = "steam",
    private val discordWebhookUrl: String?,
    private val pubgIntervalMinutes: Int = 45,
    private val checkIntervalMinutes: Int = 5
) {
    private val discord = discordWebhookUrl?.let { DiscordWebhook(it) }
    private val pubgClient: PubgApiClient? = run {
        val apiKey = config.EnvConfig.pubgApiKey()
        if (apiKey != null) PubgApiClient(apiKey) else null
    }

    private val scheduledModules = mutableListOf<ScheduledModuleEntry>()
    private var running = false

    // Tracking
    private var lastPubgStatsTime: LocalDateTime? = null
    private val executedModuleSlots = mutableSetOf<String>()  // "HH:mm" der bereits ausgeführten Slots
    private var currentlyPlaying = mutableSetOf<String>()

    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val slotFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }

    /**
     * Fügt ein Modul mit Zeitversatz hinzu.
     *
     * @param module Das Modul
     * @param minuteOffset 0 = volle Stunde, 30 = halbe Stunde
     * @param evenHoursOnly Nur zu geraden Stunden
     * @param oddHoursOnly Nur zu ungeraden Stunden
     */
    fun addModule(
        module: ScheduledModule,
        minuteOffset: Int = 0,
        evenHoursOnly: Boolean = false,
        oddHoursOnly: Boolean = false
    ): CombinedObserver {
        scheduledModules.add(ScheduledModuleEntry(module, minuteOffset, evenHoursOnly, oddHoursOnly))
        return this
    }

    /**
     * Startet den Observer.
     */
    fun start() {
        if (pubgClient == null) {
            println("PUBG API Key nicht konfiguriert!")
            return
        }

        println("""

    +---------------------------------------------------------------+
    |              FeedKrake - Combined Observer                    |
    +---------------------------------------------------------------+
    |  PUBG Spieler:     ${pubgPlayers.joinToString(", ").take(43).padEnd(43)} |
    |  PUBG Intervall:   ${("$pubgIntervalMinutes Minuten (wenn aktiv)").padEnd(43)} |
    |  Check Intervall:  ${("$checkIntervalMinutes Minuten").padEnd(43)} |
    |  Module:           ${scheduledModules.size.toString().padEnd(43)} |
    +---------------------------------------------------------------+
        """.trimIndent())

        println("\nGeplante Module:")
        scheduledModules
            .sortedBy { it.minuteOffset }
            .forEach { entry ->
                val timeStr = buildString {
                    append(if (entry.minuteOffset == 0) ":00" else ":${entry.minuteOffset}")
                    if (entry.evenHoursOnly) append(" (gerade Stunden)")
                    if (entry.oddHoursOnly) append(" (ungerade Stunden)")
                }
                println("   - ${entry.module.name} $timeStr")
            }

        running = true

        // Initialer Check
        println("\nInitialer Durchlauf...")
        checkAndProcess()

        println("\nObserver laeuft... (Ctrl+C zum Beenden)")

        while (running) {
            Thread.sleep(checkIntervalMinutes * 60 * 1000L)
            checkAndProcess()
        }
    }

    /**
     * Stoppt den Observer.
     */
    fun stop() {
        running = false
        println("Combined Observer gestoppt.")
    }

    /**
     * Hauptlogik: Prüft Status und führt Module aus.
     */
    private fun checkAndProcess() {
        val now = LocalDateTime.now()
        val timestamp = now.format(timeFormatter)

        println("\n[$timestamp] Pruefe Status...")

        // 1. Prüfe PUBG Status
        val playingNow = checkWhosPlaying()

        if (playingNow.isEmpty()) {
            println("   Niemand spielt gerade PUBG - pausiere alle Ausgaben")
            if (currentlyPlaying.isNotEmpty()) {
                println("   Session beendet")
                currentlyPlaying.clear()
                lastPubgStatsTime = null
                executedModuleSlots.clear()  // Reset für nächste Session
            }
            printNextInfo(now, isActive = false)
            return
        }

        // Ab hier: Mindestens ein Spieler ist online
        println("   Aktive Spieler: ${playingNow.joinToString(", ")}")

        // 2. PUBG Stats senden (alle X Minuten)
        val shouldSendStats = lastPubgStatsTime == null ||
            ChronoUnit.MINUTES.between(lastPubgStatsTime, now) >= pubgIntervalMinutes

        if (shouldSendStats) {
            sendPubgStats(playingNow)
            lastPubgStatsTime = now
        } else {
            val minutesUntilNext = pubgIntervalMinutes - ChronoUnit.MINUTES.between(lastPubgStatsTime, now)
            println("   Naechste PUBG Stats in $minutesUntilNext Minuten")
        }

        // 3. Zeitversetzte Module ausführen
        runScheduledModules(now)

        printNextInfo(now, isActive = true)
    }

    /**
     * Führt Module aus, deren Zeit gekommen ist.
     */
    private fun runScheduledModules(now: LocalDateTime) {
        val currentMinute = now.minute
        val currentHour = now.hour
        val isEvenHour = currentHour % 2 == 0

        scheduledModules.forEach { entry ->
            // Prüfe ob die Zeit für dieses Modul gekommen ist
            val isInWindow = currentMinute >= entry.minuteOffset &&
                currentMinute < entry.minuteOffset + checkIntervalMinutes

            if (!isInWindow) return@forEach

            // Prüfe Stunden-Einschränkung
            if (entry.evenHoursOnly && !isEvenHour) return@forEach
            if (entry.oddHoursOnly && isEvenHour) return@forEach

            // Erstelle eindeutigen Slot-Key für diese Stunde und dieses Modul
            val slotKey = "${now.hour}:${entry.minuteOffset}:${entry.module.name}"

            // Prüfe ob bereits in diesem Slot ausgeführt
            if (slotKey in executedModuleSlots) return@forEach

            // Modul ausführen
            println("\n   [Minute ${entry.minuteOffset}] ${entry.module.name}...")
            try {
                val result = entry.module.execute()
                when {
                    result == null -> {
                        println("   ${entry.module.name}: Keine Daten")
                    }
                    discord == null -> {
                        println("   ${entry.module.name}: OK (kein Webhook)")
                        println(result.lines().take(5).joinToString("\n") { "      $it" })
                        if (result.lines().size > 5) println("      ...")
                    }
                    else -> {
                        discord.send(result)
                        println("   ${entry.module.name}: Gesendet")
                    }
                }
                executedModuleSlots.add(slotKey)
            } catch (e: Exception) {
                println("   ${entry.module.name}: Fehler - ${e.message}")
            }
        }

        // Alte Slot-Keys aufräumen (nur aktuelle Stunde behalten)
        executedModuleSlots.removeIf { !it.startsWith("${now.hour}:") }
    }

    /**
     * Prüft wer gerade PUBG spielt.
     */
    private fun checkWhosPlaying(): List<String> {
        val playing = mutableListOf<String>()

        pubgPlayers.forEach { playerName ->
            try {
                val isPlaying = checkIfPlaying(playerName)
                if (isPlaying) {
                    playing.add(playerName)
                }
            } catch (e: Exception) {
                println("   Fehler bei $playerName: ${e.message}")
            }
        }

        currentlyPlaying.clear()
        currentlyPlaying.addAll(playing)

        return playing
    }

    /**
     * Prüft ob ein Spieler gerade spielt.
     * Spieler gilt als aktiv wenn letztes Match < 45 Min her ist.
     */
    private fun checkIfPlaying(playerName: String): Boolean {
        val client = pubgClient ?: return false

        val accountId = client.fetchAccountId(playerName, pubgPlatform) ?: return false
        val lastMatchTime = client.fetchLatestMatchTimestamp(pubgPlatform, accountId) ?: return false

        val matchTime = LocalDateTime.ofInstant(lastMatchTime, ZoneId.systemDefault())
        val minutesSinceMatch = ChronoUnit.MINUTES.between(matchTime, LocalDateTime.now())
        return minutesSinceMatch < 45
    }

    /**
     * Sendet PUBG Statistiken.
     */
    private fun sendPubgStats(players: List<String>) {
        val client = pubgClient ?: return

        println("\n   Sende PUBG Statistiken...")

        players.forEach { playerName ->
            try {
                val accountId = client.fetchAccountId(playerName, pubgPlatform) ?: return@forEach

                val dailyStats = client.fetchRecentStats(pubgPlatform, accountId, hours = 24, maxMatches = 30)
                val weeklyStats = client.fetchRecentStats(pubgPlatform, accountId, hours = 168, maxMatches = 50)

                if (dailyStats != null || weeklyStats != null) {
                    val message = formatPubgStats(playerName, dailyStats, weeklyStats)
                    discord?.send(message)
                    println("   Stats fuer $playerName gesendet")
                }
            } catch (e: Exception) {
                println("   Fehler bei $playerName: ${e.message}")
            }
        }
    }

    /**
     * Formatiert PUBG Stats für Discord.
     */
    private fun formatPubgStats(
        playerName: String,
        dailyStats: PlayerStats?,
        weeklyStats: PlayerStats?
    ): String {
        return buildString {
            appendLine("👥 Player: $playerName (${pubgPlatform.replaceFirstChar { it.uppercase() }})")
            appendLine()

            if (dailyStats != null && dailyStats.matches > 0) {
                appendLine(dailyStats.basicFormat("📅 Tagesstatistik:"))
                appendLine()
            }

            if (weeklyStats != null && weeklyStats.matches > 0) {
                appendLine(weeklyStats.basicFormat("🗓 Wochenstatistik:"))
                append(weeklyStats.weeklyExtras())
            }
        }.trimEnd()
    }

    private fun printNextInfo(now: LocalDateTime, isActive: Boolean) {
        val nextCheck = now.plusMinutes(checkIntervalMinutes.toLong())

        println("\n   Naechster Check: ${nextCheck.format(timeFormatter)}")

        if (isActive) {
            // Zeige nächste geplante Module
            val currentMinute = now.minute
            val upcoming = scheduledModules
                .filter { entry ->
                    val slotKey = "${now.hour}:${entry.minuteOffset}:${entry.module.name}"
                    slotKey !in executedModuleSlots
                }
                .sortedBy { entry ->
                    if (entry.minuteOffset > currentMinute) entry.minuteOffset
                    else entry.minuteOffset + 60  // Nächste Stunde
                }
                .take(2)

            if (upcoming.isNotEmpty()) {
                upcoming.forEach { entry ->
                    val targetMinute = entry.minuteOffset
                    val targetTime = if (targetMinute > currentMinute) {
                        now.withMinute(targetMinute).withSecond(0)
                    } else {
                        now.plusHours(1).withMinute(targetMinute).withSecond(0)
                    }
                    println("   ${entry.module.name}: ${targetTime.format(timeFormatter)}")
                }
            }

            if (lastPubgStatsTime != null) {
                val nextStats = lastPubgStatsTime!!.plusMinutes(pubgIntervalMinutes.toLong())
                println("   Naechste PUBG Stats: ${nextStats.format(timeFormatter)}")
            }
        } else {
            println("   Warte auf Spieler-Aktivitaet...")
        }
    }
}
