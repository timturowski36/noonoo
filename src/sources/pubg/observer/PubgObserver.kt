package sources.pubg.observer

import config.EnvConfig
import scheduler.discord.DiscordWebhook
import sources.`pubg-api`.api.PubgApiClient
import sources.pubg.model.PlayerStats
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * PUBG Observer - Beobachtet Spieler und postet Stats wenn sie spielen.
 *
 * Alle 30 Minuten wird geprüft, ob ein Spieler ein neues Match gespielt hat.
 * Wenn ja, werden Tages- und Wochenstatistiken in Discord gepostet.
 */
class PubgObserver(
    private val players: List<String>,
    private val platform: String = "steam",
    private val discordWebhookUrl: String,
    private val checkIntervalMinutes: Int = 30
) {
    private val pubgClient: PubgApiClient
    private val discord: DiscordWebhook
    private val timezone = ZoneId.of("Europe/Berlin")

    // Speichert den letzten bekannten Match-Zeitstempel pro Spieler
    private val lastKnownMatch = ConcurrentHashMap<String, Instant>()

    // Speichert die Account-IDs pro Spieler
    private val accountIds = ConcurrentHashMap<String, String>()

    private var running = false

    init {
        val apiKey = EnvConfig.pubgApiKey()
            ?: throw IllegalStateException("PUBG_API_KEY nicht in .env konfiguriert!")
        pubgClient = PubgApiClient(apiKey)
        discord = DiscordWebhook(discordWebhookUrl)
    }

    /**
     * Startet den Observer. Läuft bis stop() aufgerufen wird.
     */
    fun start() {
        println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║              🎮 PUBG Observer                                 ║
    ╠═══════════════════════════════════════════════════════════════╣
    ║  Spieler:    ${players.joinToString(", ").take(45).padEnd(45)} ║
    ║  Platform:   ${platform.padEnd(45)} ║
    ║  Intervall:  ${("$checkIntervalMinutes Minuten").padEnd(45)} ║
    ╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent())

        // Account-IDs für alle Spieler laden
        println("\n🔍 Lade Account-IDs...")
        players.forEach { playerName ->
            val accountId = pubgClient.fetchAccountId(playerName, platform)
            if (accountId != null) {
                accountIds[playerName] = accountId
                println("   ✅ $playerName: $accountId")
            } else {
                println("   ❌ $playerName: Nicht gefunden!")
            }
        }

        if (accountIds.isEmpty()) {
            println("❌ Keine Spieler gefunden! Observer beendet.")
            return
        }

        // Initialen Check durchführen (ohne Discord-Post)
        println("\n📊 Initialer Status-Check...")
        initializeLastMatches()

        running = true
        println("\n🕐 Observer läuft... (Ctrl+C zum Beenden)")
        println("   Nächster Check in $checkIntervalMinutes Minuten\n")

        while (running) {
            Thread.sleep(checkIntervalMinutes * 60 * 1000L)
            checkForActivity()
        }
    }

    /**
     * Stoppt den Observer.
     */
    fun stop() {
        running = false
        println("🛑 Observer gestoppt.")
    }

    /**
     * Initialisiert die letzten bekannten Match-Zeitstempel.
     */
    private fun initializeLastMatches() {
        accountIds.forEach { (playerName, accountId) ->
            val latestMatch = pubgClient.fetchLatestMatchTimestamp(platform, accountId)
            if (latestMatch != null) {
                lastKnownMatch[playerName] = latestMatch
                val formattedTime = LocalDateTime.ofInstant(latestMatch, timezone)
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                println("   📍 $playerName: Letztes Match um $formattedTime")
            } else {
                println("   ⚠️ $playerName: Kein Match gefunden")
            }
        }
    }

    /**
     * Prüft ob Spieler aktiv sind und postet ggf. Stats.
     */
    private fun checkForActivity() {
        val timestamp = LocalDateTime.now(timezone)
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        println("⏰ [$timestamp] Prüfe Aktivität...")

        val activePlayers = mutableListOf<String>()

        accountIds.forEach { (playerName, accountId) ->
            val latestMatch = pubgClient.fetchLatestMatchTimestamp(platform, accountId)
            val lastKnown = lastKnownMatch[playerName]

            if (latestMatch != null) {
                if (lastKnown == null || latestMatch.isAfter(lastKnown)) {
                    println("   🎮 $playerName hat gespielt!")
                    lastKnownMatch[playerName] = latestMatch
                    activePlayers.add(playerName)
                } else {
                    println("   💤 $playerName: Keine neuen Matches")
                }
            }
        }

        // Stats für aktive Spieler posten
        if (activePlayers.isNotEmpty()) {
            println("   📤 Poste Stats für: ${activePlayers.joinToString(", ")}")
            postStatsForPlayers(activePlayers)
        }

        println("   ✅ Check abgeschlossen. Nächster Check in $checkIntervalMinutes Minuten.\n")
    }

    /**
     * Postet Stats für die angegebenen Spieler in Discord.
     */
    private fun postStatsForPlayers(playerNames: List<String>) {
        playerNames.forEach { playerName ->
            val accountId = accountIds[playerName] ?: return@forEach

            // Tagesstats (letzte 24h)
            val dailyStats = pubgClient.fetchRecentStats(platform, accountId, hours = 24)

            // Wochenstats (letzte 168h = 7 Tage)
            val weeklyStats = pubgClient.fetchRecentStats(platform, accountId, hours = 168)

            if (dailyStats != null || weeklyStats != null) {
                val message = formatPlayerStats(playerName, dailyStats, weeklyStats)
                discord.send(message)
            }
        }
    }

    /**
     * Formatiert die Stats für Discord.
     */
    private fun formatPlayerStats(
        playerName: String,
        dailyStats: PlayerStats?,
        weeklyStats: PlayerStats?
    ): String {
        val now = LocalDateTime.now(timezone)
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

        return buildString {
            appendLine("🎮 **Player: $playerName** (${platform.replaceFirstChar { it.uppercase() }})")

            if (dailyStats != null && dailyStats.matches > 0) {
                appendLine("📊 **Tagesstatistik:**")
                appendLine(formatStatsLine(dailyStats))
            }

            if (weeklyStats != null && weeklyStats.matches > 0) {
                appendLine("📅 **Wochenstatistik:**")
                appendLine(formatStatsLine(weeklyStats))
                appendLine(formatExtras(weeklyStats))
            }

            append("🕐 Stand: $now")
        }
    }

    private fun formatStatsLine(stats: PlayerStats): String {
        val winsStr = if (stats.wins == 0) "-" else "${stats.wins}"
        return "Matches: ${stats.matches}   Wins: $winsStr   K/D: ${stats.kdFormatted()}   Kills: ${stats.kills}   Assists: ${stats.assists}   Ø Schaden: ${stats.avgDamageFormatted()}"
    }

    private fun formatExtras(stats: PlayerStats): String {
        return buildString {
            appendLine("Weitester Kill: ${"%.0f".format(stats.longestKill)}m")
            appendLine("Headshots: ${stats.headshotKills} (${"%.0f".format(stats.headshotRate)}%)")
            appendLine("Revives: ${stats.revives}")
            appendLine("Knockdowns: ${stats.knockdowns}")
            append("Top 10: ${stats.topTens} (${"%.0f".format(stats.topTenRate)}%)")
        }
    }
}
