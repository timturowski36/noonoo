package scheduler

import scheduler.discord.DiscordWebhook
import sources.bundesliga.api.TabellenApiClient
import sources.bundesliga.model.TabellenEintrag
import sources.handball.api.HandballScraper
import sources.handball.api.HandballTableScraper
import sources.handball.cache.HandballCache
import sources.handball.cache.HandballTableCache
import sources.handball.model.HandballScheduleData
import sources.handball.model.HandballTableData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

/**
 * Ein Modul das vom Scheduler ausgeführt werden kann.
 */
interface ScheduledModule {
    val name: String
    fun execute(): String?  // Gibt Discord-Nachricht zurück oder null
}

/**
 * Multi-Module Scheduler für FeedKrake.
 *
 * Führt verschiedene Module zeitversetzt aus:
 * - Primäre Module (z.B. PUBG): Alle 30 Minuten (Minute 0, 30)
 * - Sekundäre Module (z.B. Handball): Alle 30 Minuten versetzt (Minute 15, 45)
 */
class MultiModuleScheduler(
    private val discordWebhookUrl: String?,
    private val intervalMinutes: Int = 30
) {
    private val discord = discordWebhookUrl?.let { DiscordWebhook(it) }
    private val primaryModules = mutableListOf<ScheduledModule>()
    private val secondaryModules = mutableListOf<ScheduledModule>()
    private var running = false

    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }

    /**
     * Fügt ein primäres Modul hinzu (läuft zur vollen/halben Stunde).
     */
    fun addPrimaryModule(module: ScheduledModule): MultiModuleScheduler {
        primaryModules.add(module)
        return this
    }

    /**
     * Fügt ein sekundäres Modul hinzu (läuft 15 Minuten versetzt).
     */
    fun addSecondaryModule(module: ScheduledModule): MultiModuleScheduler {
        secondaryModules.add(module)
        return this
    }

    /**
     * Startet den Scheduler.
     */
    fun start() {
        println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║              🐙 FeedKrake Multi-Module Scheduler              ║
    ╠═══════════════════════════════════════════════════════════════╣
    ║  Primäre Module:   ${primaryModules.size.toString().padEnd(43)} ║
    ║  Sekundäre Module: ${secondaryModules.size.toString().padEnd(43)} ║
    ║  Intervall:        ${("$intervalMinutes Minuten").padEnd(43)} ║
    ╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent())

        // Liste Module
        println("\n📋 Primäre Module (Minute 0, 30):")
        primaryModules.forEach { println("   • ${it.name}") }
        println("\n📋 Sekundäre Module (Minute 15, 45):")
        secondaryModules.forEach { println("   • ${it.name}") }

        // Initialer Durchlauf
        println("\n🚀 Initialer Durchlauf...")
        runPrimaryModules()
        Thread.sleep(1000)
        runSecondaryModules()

        running = true

        // Starte zwei Threads für primäre und sekundäre Module
        val primaryThread = thread(name = "Primary-Scheduler") {
            scheduleLoop(primaryModules, 0)
        }

        val secondaryThread = thread(name = "Secondary-Scheduler") {
            // 15 Minuten versetzt
            Thread.sleep(15 * 60 * 1000L)
            scheduleLoop(secondaryModules, 15)
        }

        println("\n🕐 Scheduler läuft... (Ctrl+C zum Beenden)")
        printNextRuns()
    }

    /**
     * Stoppt den Scheduler.
     */
    fun stop() {
        running = false
        println("🛑 Scheduler gestoppt.")
    }

    private fun scheduleLoop(modules: List<ScheduledModule>, offsetMinutes: Int) {
        while (running) {
            Thread.sleep(intervalMinutes * 60 * 1000L)

            val now = LocalDateTime.now()
            println("\n⏰ [${now.format(timeFormatter)}] Führe ${modules.size} Module aus...")

            modules.forEach { module ->
                try {
                    val result = module.execute()
                    if (result != null && discord != null) {
                        discord.send(result)
                        println("   ✅ ${module.name}: Gesendet")
                    } else {
                        println("   ℹ️ ${module.name}: Keine Ausgabe")
                    }
                } catch (e: Exception) {
                    println("   ❌ ${module.name}: ${e.message}")
                }
            }
        }
    }

    private fun runPrimaryModules() {
        println("\n🔵 Primäre Module:")
        primaryModules.forEach { module ->
            try {
                val result = module.execute()
                if (result != null && discord != null) {
                    discord.send(result)
                    println("   ✅ ${module.name}: Gesendet")
                } else {
                    println("   ℹ️ ${module.name}: Keine Ausgabe")
                }
            } catch (e: Exception) {
                println("   ❌ ${module.name}: ${e.message}")
            }
        }
    }

    private fun runSecondaryModules() {
        println("\n🟢 Sekundäre Module:")
        secondaryModules.forEach { module ->
            try {
                val result = module.execute()
                if (result != null && discord != null) {
                    discord.send(result)
                    println("   ✅ ${module.name}: Gesendet")
                } else {
                    println("   ℹ️ ${module.name}: Keine Ausgabe")
                }
            } catch (e: Exception) {
                println("   ❌ ${module.name}: ${e.message}")
            }
        }
    }

    private fun printNextRuns() {
        val now = LocalDateTime.now()
        val nextPrimary = now.plusMinutes(intervalMinutes.toLong())
        val nextSecondary = now.plusMinutes(15)

        println("\n   📅 Nächste Ausführung:")
        println("      Primär:   ${nextPrimary.format(dateTimeFormatter)}")
        println("      Sekundär: ${nextSecondary.format(dateTimeFormatter)}")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Vorgefertigte Module
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Modul: Handball kommende Spiele
 */
class HandballUpcomingModule(
    private val teamId: String,
    private val teamName: String = "HSG RE/OE"
) : ScheduledModule {
    override val name = "Handball: Kommende Spiele"
    private val scraper = HandballScraper()
    private val cache = HandballCache()

    override fun execute(): String? {
        val data = getScheduleData() ?: return null
        val upcoming = data.upcomingMatches(5)
        if (upcoming.isEmpty()) return null
        return data.discordUpcomingFormat()
    }

    private fun getScheduleData(): HandballScheduleData? {
        return if (cache.isValid()) cache.load() else {
            scraper.fetchSchedule(teamId)?.also { cache.save(it) }
        }
    }
}

/**
 * Modul: Handball letzte Ergebnisse
 */
class HandballResultsModule(
    private val teamId: String,
    private val teamName: String = "HSG RE/OE"
) : ScheduledModule {
    override val name = "Handball: Letzte Ergebnisse"
    private val scraper = HandballScraper()
    private val cache = HandballCache()

    override fun execute(): String? {
        val data = getScheduleData() ?: return null
        val results = data.recentResults(5)
        if (results.isEmpty()) return null
        return data.discordResultsFormat()
    }

    private fun getScheduleData(): HandballScheduleData? {
        return if (cache.isValid()) cache.load() else {
            scraper.fetchSchedule(teamId)?.also { cache.save(it) }
        }
    }
}

/**
 * Modul: Handball Tabelle
 */
class HandballTableModule(
    private val teamId: String,
    private val teamName: String = "HSG RE/OE"
) : ScheduledModule {
    override val name = "Handball: Tabelle"
    private val scraper = HandballTableScraper()
    private val cache = HandballTableCache()
    private val scheduleCache = HandballCache()

    override fun execute(): String? {
        val data = getTableData() ?: return null
        return data.discordFormat(teamName)  // Komplette Tabelle
    }

    private fun getTableData(): HandballTableData? {
        // Hole Zeitstempel des letzten Spiels
        val scheduleData = scheduleCache.load()
        val lastMatchTime = scheduleData?.recentResults(1)?.firstOrNull()?.date

        return if (cache.isValid(lastMatchTime)) {
            cache.load()
        } else {
            scraper.fetchTable(teamId)?.also { cache.save(it) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Bundesliga Module
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Modul: Bundesliga Tabelle (1. oder 2. Liga)
 */
class BundesligaTableModule(
    private val liga: String = "bl1",  // bl1 = 1. Bundesliga, bl2 = 2. Bundesliga
    private val maxTeams: Int = 18
) : ScheduledModule {
    override val name = when (liga) {
        "bl1" -> "Fussball: 1. Bundesliga Tabelle"
        "bl2" -> "Fussball: 2. Bundesliga Tabelle"
        else -> "Fussball: $liga Tabelle"
    }

    private val apiClient = TabellenApiClient()

    override fun execute(): String? {
        val saison = ermittleAktuelleSaison()
        val tabelle = apiClient.fetchTabelle(liga, saison)
        if (tabelle.isEmpty()) return null
        return formatTabelle(tabelle.take(maxTeams))
    }

    private fun ermittleAktuelleSaison(): String {
        val currentYear = LocalDate.now().year
        val currentMonth = LocalDate.now().monthValue
        return if (currentMonth >= 8) currentYear.toString() else (currentYear - 1).toString()
    }

    private fun formatTabelle(tabelle: List<TabellenEintrag>): String {
        val ligaName = when (liga) {
            "bl1" -> "1. Bundesliga"
            "bl2" -> "2. Bundesliga"
            else -> liga
        }

        return buildString {
            appendLine("**$ligaName - Tabelle**")
            appendLine("```")
            appendLine(" #  | Team                | Sp | S  | U | N  | Tore  | Diff | Pkt")
            appendLine("----|---------------------|----|----|---|----|-------|------|----")

            tabelle.forEach { e ->
                val platz = e.platz.toString().padStart(2)
                val team = e.shortName.take(19).padEnd(19)
                val spiele = e.matches.toString().padStart(2)
                val siege = e.won.toString().padStart(2)
                val unent = e.draw.toString().padStart(1)
                val nied = e.lost.toString().padStart(2)
                val tore = "${e.goals}:${e.opponentGoals}".padStart(5)
                val diff = (if (e.goalDiff >= 0) "+${e.goalDiff}" else "${e.goalDiff}").padStart(4)
                val punkte = e.points.toString().padStart(3)

                appendLine(" $platz | $team | $spiele | $siege | $unent | $nied | $tore | $diff | $punkte")
            }
            append("```")
        }
    }

    companion object {
        /** 1. Bundesliga Tabelle */
        fun ersteLiga(maxTeams: Int = 18) = BundesligaTableModule("bl1", maxTeams)

        /** 2. Bundesliga Tabelle */
        fun zweiteLiga(maxTeams: Int = 18) = BundesligaTableModule("bl2", maxTeams)
    }
}
