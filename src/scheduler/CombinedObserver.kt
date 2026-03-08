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
 * Kombinierter Observer für FeedKrake.
 *
 * Ablauf:
 * 1. Prüft ob jemand PUBG spielt
 * 2. Wenn ja: PUBG Stats alle 45 Minuten senden
 * 3. Zu jeder vollen Stunde: Alle sekundären Module durchgehen
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

    private val secondaryModules = mutableListOf<ScheduledModule>()
    private var running = false

    // Tracking
    private var lastPubgStatsTime: LocalDateTime? = null
    private var lastHourlyRunHour: Int = -1
    private var currentlyPlaying = mutableSetOf<String>()

    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }

    /**
     * Fügt ein sekundäres Modul hinzu (läuft zur vollen Stunde).
     */
    fun addModule(module: ScheduledModule): CombinedObserver {
        secondaryModules.add(module)
        return this
    }

    /**
     * Startet den Observer.
     */
    fun start() {
        if (pubgClient == null) {
            println("❌ PUBG API Key nicht konfiguriert!")
            return
        }

        println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║              🐙 FeedKrake - Combined Observer                 ║
    ╠═══════════════════════════════════════════════════════════════╣
    ║  PUBG Spieler:     ${pubgPlayers.joinToString(", ").take(43).padEnd(43)} ║
    ║  PUBG Intervall:   ${("$pubgIntervalMinutes Minuten (wenn aktiv)").padEnd(43)} ║
    ║  Check Intervall:  ${("$checkIntervalMinutes Minuten").padEnd(43)} ║
    ║  Module:           ${secondaryModules.size.toString().padEnd(43)} ║
    ╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent())

        println("\n📋 Sekundäre Module (jede volle Stunde):")
        secondaryModules.forEach { println("   • ${it.name}") }

        running = true

        // Initialer Check
        println("\n🚀 Initialer Durchlauf...")
        checkAndProcess()

        println("\n🕐 Observer läuft... (Ctrl+C zum Beenden)")

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
        println("🛑 Combined Observer gestoppt.")
    }

    /**
     * Hauptlogik: Prüft Status und führt Module aus.
     */
    private fun checkAndProcess() {
        val now = LocalDateTime.now()
        val timestamp = now.format(timeFormatter)

        println("\n⏰ [$timestamp] Prüfe Status...")

        // 1. Prüfe PUBG Status
        val playingNow = checkWhosPlaying()

        if (playingNow.isNotEmpty()) {
            println("   🎮 Aktive Spieler: ${playingNow.joinToString(", ")}")

            // Prüfe ob 45 Minuten seit letzten Stats vergangen sind
            val shouldSendStats = lastPubgStatsTime == null ||
                ChronoUnit.MINUTES.between(lastPubgStatsTime, now) >= pubgIntervalMinutes

            if (shouldSendStats) {
                sendPubgStats(playingNow)
                lastPubgStatsTime = now
            } else {
                val minutesUntilNext = pubgIntervalMinutes - ChronoUnit.MINUTES.between(lastPubgStatsTime, now)
                println("   ⏳ Nächste PUBG Stats in $minutesUntilNext Minuten")
            }
        } else {
            println("   💤 Niemand spielt gerade PUBG")
            // Reset wenn niemand mehr spielt
            if (currentlyPlaying.isNotEmpty()) {
                println("   📊 Session beendet - Stats werden beim nächsten Spiel gesendet")
                currentlyPlaying.clear()
            }
        }

        // 2. Prüfe ob volle Stunde erreicht
        val currentHour = now.hour
        if (currentHour != lastHourlyRunHour) {
            println("\n   🕐 Volle Stunde erreicht - führe Module aus...")
            runSecondaryModules()
            lastHourlyRunHour = currentHour
        }

        printNextInfo(now)
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
                println("   ⚠️ Fehler bei $playerName: ${e.message}")
            }
        }

        currentlyPlaying.clear()
        currentlyPlaying.addAll(playing)

        return playing
    }

    /**
     * Prüft ob ein Spieler gerade spielt (hat kürzlich ein Match gespielt).
     */
    private fun checkIfPlaying(playerName: String): Boolean {
        val client = pubgClient ?: return false

        // Hole Account-ID und letztes Match
        val accountId = client.fetchAccountId(playerName, pubgPlatform) ?: return false
        val lastMatchTime = client.fetchLatestMatchTimestamp(pubgPlatform, accountId) ?: return false

        // Konvertiere zu LocalDateTime
        val matchTime = LocalDateTime.ofInstant(lastMatchTime, ZoneId.systemDefault())

        // Spieler gilt als "spielend" wenn letztes Match < 30 Min her
        val minutesSinceMatch = ChronoUnit.MINUTES.between(matchTime, LocalDateTime.now())
        return minutesSinceMatch < 30
    }

    /**
     * Sendet PUBG Statistiken.
     */
    private fun sendPubgStats(players: List<String>) {
        val client = pubgClient ?: return

        println("\n   📊 Sende PUBG Statistiken...")

        players.forEach { playerName ->
            try {
                val accountId = client.fetchAccountId(playerName, pubgPlatform) ?: return@forEach

                // Hole Stats der letzten 12 Stunden
                val recentStats = client.fetchRecentStats(pubgPlatform, accountId, hours = 12, maxMatches = 20)
                val lifetimeWins = client.fetchLifetimeWins(pubgPlatform, accountId)

                if (recentStats != null) {
                    val message = formatPubgStats(playerName, recentStats, lifetimeWins)
                    discord?.send(message)
                    println("   ✅ Stats für $playerName gesendet")
                }
            } catch (e: Exception) {
                println("   ❌ Fehler bei $playerName: ${e.message}")
            }
        }
    }

    /**
     * Formatiert PUBG Stats für Discord.
     */
    private fun formatPubgStats(playerName: String, stats: PlayerStats, lifetimeWins: Int?): String {
        return buildString {
            appendLine("🎮 **PUBG Stats: $playerName** (letzte 12h)")
            appendLine("```")
            appendLine("Matches: ${stats.matches}")
            appendLine("Wins: ${stats.wins}")
            appendLine("K/D: ${stats.kdFormatted()}")
            appendLine("Kills: ${stats.kills}   Assists: ${stats.assists}")
            appendLine("Ø Schaden: ${stats.avgDamageFormatted()}")
            appendLine("Top 10: ${stats.topTens} (${String.format("%.0f", stats.topTenRate)}%)")
            if (lifetimeWins != null) {
                appendLine("─".repeat(25))
                appendLine("Lifetime Wins: $lifetimeWins")
            }
            appendLine("```")
            append("🕐 ${LocalDateTime.now().format(dateTimeFormatter)}")
        }
    }

    /**
     * Führt alle sekundären Module aus.
     */
    private fun runSecondaryModules() {
        secondaryModules.forEach { module ->
            try {
                val result = module.execute()
                when {
                    result == null -> {
                        println("   ℹ️ ${module.name}: Keine Daten")
                    }
                    discord == null -> {
                        println("   📋 ${module.name}: OK (kein Webhook)")
                        // Zeige Vorschau in Konsole
                        println(result.lines().take(5).joinToString("\n") { "      $it" })
                        if (result.lines().size > 5) println("      ...")
                    }
                    else -> {
                        discord.send(result)
                        println("   ✅ ${module.name}: Gesendet")
                    }
                }
            } catch (e: Exception) {
                println("   ❌ ${module.name}: ${e.message}")
            }
        }
    }

    private fun printNextInfo(now: LocalDateTime) {
        val nextCheck = now.plusMinutes(checkIntervalMinutes.toLong())
        val nextHour = now.plusHours(1).withMinute(0).withSecond(0)

        println("\n   📅 Nächster Check: ${nextCheck.format(timeFormatter)}")
        println("   📅 Nächste volle Stunde: ${nextHour.format(timeFormatter)}")

        if (currentlyPlaying.isNotEmpty() && lastPubgStatsTime != null) {
            val nextStats = lastPubgStatsTime!!.plusMinutes(pubgIntervalMinutes.toLong())
            println("   📅 Nächste PUBG Stats: ${nextStats.format(timeFormatter)}")
        }
    }
}
