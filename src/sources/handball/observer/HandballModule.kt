package sources.handball.observer

import scheduler.discord.DiscordWebhook
import sources.handball.api.HandballScraper
import sources.handball.cache.HandballCache
import sources.handball.model.HandballScheduleData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Handball-Modul für FeedKrake.
 *
 * Lädt Spielplandaten von handball.net, cached sie,
 * und kann Discord-Benachrichtigungen senden.
 *
 * Verwendung:
 * 1. Als eigenständiges Modul: HandballModule.run() einmalig ausführen
 * 2. Als Observer: HandballModule.startObserver() für kontinuierliche Überwachung
 * 3. Integration: getScheduleData() für Daten ohne Discord-Output
 */
class HandballModule(
    private val teamId: String = "handball4all.westfalen.1309001",
    private val discordWebhookUrl: String? = null,
    private val seasonFrom: String = "2025-07-01",
    private val seasonTo: String = "2026-06-30"
) {
    private val scraper = HandballScraper()
    private val cache = HandballCache()
    private val discord = discordWebhookUrl?.let { DiscordWebhook(it) }

    private var running = false

    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }

    /**
     * Führt einen einzelnen Durchlauf aus:
     * - Lädt Daten (aus Cache oder von der Webseite)
     * - Postet nächste Spiele in Discord (wenn konfiguriert)
     */
    fun run(): HandballScheduleData? {
        println("\n🤾 ══════════════════════════════════════════════")
        println("   Handball Module - Einzeldurchlauf")
        println("   ══════════════════════════════════════════════")

        val data = getScheduleData()

        if (data == null) {
            println("❌ Keine Daten verfügbar")
            return null
        }

        // Konsolen-Ausgabe
        printScheduleSummary(data)

        // Discord-Ausgabe (wenn konfiguriert)
        if (discord != null) {
            postToDiscord(data)
        }

        return data
    }

    /**
     * Holt Spielplandaten (cached oder frisch).
     */
    fun getScheduleData(): HandballScheduleData? {
        // Prüfe Cache
        if (cache.isValid()) {
            println("📂 Verwende Cache...")
            return cache.load()
        }

        // Lade frisch
        println("🌐 Lade von handball.net...")
        val data = scraper.fetchSchedule(teamId, seasonFrom, seasonTo)

        if (data != null) {
            cache.save(data)
        }

        return data
    }

    /**
     * Startet den Observer-Modus.
     * Prüft regelmäßig ob ein neues Spiel stattgefunden hat.
     */
    fun startObserver(checkIntervalMinutes: Int = 60) {
        println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║              🤾 Handball Observer                             ║
    ╠═══════════════════════════════════════════════════════════════╣
    ║  Team:       ${teamId.take(45).padEnd(45)} ║
    ║  Intervall:  ${("$checkIntervalMinutes Minuten").padEnd(45)} ║
    ╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent())

        // Initialer Durchlauf
        val initialData = run()
        if (initialData == null) {
            println("⚠️ Konnte keine initialen Daten laden. Observer beendet.")
            return
        }

        running = true
        println("\n🕐 Observer läuft... (Ctrl+C zum Beenden)")
        printNextCheckInfo(checkIntervalMinutes)

        while (running) {
            Thread.sleep(checkIntervalMinutes * 60 * 1000L)
            checkAndUpdate()
            printNextCheckInfo(checkIntervalMinutes)
        }
    }

    /**
     * Stoppt den Observer.
     */
    fun stop() {
        running = false
        println("🛑 Handball Observer gestoppt.")
    }

    /**
     * Prüft ob neue Daten geladen werden sollten.
     */
    private fun checkAndUpdate() {
        val timestamp = LocalDateTime.now().format(timeFormatter)
        println("\n⏰ [$timestamp] Prüfe Handball-Status...")

        if (cache.shouldRefreshForNewResult()) {
            println("🔄 Neues Ergebnis möglich - lade neu...")

            // Force-Refresh durch Cache löschen
            cache.clear()
            val newData = getScheduleData()

            if (newData != null && discord != null) {
                // Poste Ergebnis des letzten Spiels
                val lastResult = newData.recentResults(1).firstOrNull()
                if (lastResult != null) {
                    val message = buildString {
                        appendLine("🏆 **Neues Spielergebnis!**")
                        appendLine()
                        appendLine(lastResult.resultFormat(newData.teamName))
                        appendLine()
                        append("_Nächstes Spiel: ${newData.nextMatch()?.upcomingFormat(newData.teamName) ?: "keins geplant"}_")
                    }
                    discord.send(message)
                }
            }
        } else {
            println("   💤 Kein neues Spiel - Cache gültig")
        }
    }

    private fun printScheduleSummary(data: HandballScheduleData) {
        println("\n📋 ${data.teamName} - Saison ${data.season}")
        println("   Gesamt: ${data.matches.size} Spiele")
        println("   Gespielt: ${data.matches.count { it.isPlayed }}")
        println("   Kommend: ${data.matches.count { !it.isPlayed }}")

        val next = data.nextMatch()
        if (next != null) {
            val daysUntil = ChronoUnit.DAYS.between(LocalDateTime.now(), next.date)
            println("\n   ⏭️ Nächstes Spiel in $daysUntil Tagen:")
            println("      ${next.upcomingFormat(data.teamName)}")
        }
    }

    private fun postToDiscord(data: HandballScheduleData) {
        val discord = this.discord ?: return

        println("\n📤 Sende an Discord...")
        val message = data.discordUpcomingFormat()

        if (discord.send(message)) {
            println("✅ Gesendet!")
        }
    }

    private fun printNextCheckInfo(intervalMinutes: Int) {
        val nextCheck = LocalDateTime.now().plusMinutes(intervalMinutes.toLong())
        val nextMatch = cache.getNextMatchDate()

        println("   📅 Nächster Check: ${nextCheck.format(dateTimeFormatter)}")
        if (nextMatch != null) {
            println("   🏟️ Nächstes Spiel: ${nextMatch.format(dateTimeFormatter)}")
        }
    }
}
