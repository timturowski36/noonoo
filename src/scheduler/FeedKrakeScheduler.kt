package scheduler

import outputs.discord.DiscordBot
import scheduler.config.FeedKrakeConfig
import scheduler.config.JobConfig
import scheduler.discord.DiscordWebhook
import sources.heise.HeiseSource
import sources.heise.config.HeiseModuleConfig
import sources.heise.model.HeiseFeed
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Vereinfachter Scheduler für FeedKrake.
 * Nutzt die zentrale feedkrake.config und den bestehenden DiscordBot.
 */
class FeedKrakeScheduler {

    private val config = FeedKrakeConfig.load()
    private val timezone = ZoneId.of("Europe/Berlin")
    private val lastExecuted = ConcurrentHashMap<String, LocalDateTime>()
    private var running = false

    fun start() {
        println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║              🐙 FeedKrake Scheduler                           ║
    ╠═══════════════════════════════════════════════════════════════╣
    ║  Channels: ${config.channels.size.toString().padEnd(3)}                                            ║
    ║  Jobs:     ${config.jobs.size.toString().padEnd(3)}                                            ║
    ╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent())

        printJobOverview()

        // Jobs mit "beim start" sofort ausführen
        config.jobs
            .filter { it.schedule.equals("beim start", ignoreCase = true) }
            .forEach { executeJob(it) }

        running = true
        println("\n🕐 Scheduler läuft... (Ctrl+C zum Beenden)\n")

        while (running) {
            val now = LocalDateTime.now(timezone)

            config.jobs
                .filter { !it.schedule.equals("beim start", ignoreCase = true) }
                .filter { shouldExecute(it, now) }
                .forEach { job ->
                    executeJob(job)
                    lastExecuted[job.name] = now
                }

            Thread.sleep(60_000) // Jede Minute prüfen
        }
    }

    fun stop() {
        running = false
    }

    private fun printJobOverview() {
        println("📋 Konfigurierte Jobs:")
        println("─".repeat(70))
        config.jobs.forEach { job ->
            val channelOk = config.channels.containsKey(job.channel)
            val status = if (channelOk) "✅" else "❌"
            println("$status ${job.name.padEnd(20)} | ${job.module.padEnd(18)} | ${job.schedule.padEnd(15)} → #${job.channel}")
        }
        println("─".repeat(70))

        // Warnungen für fehlende Channels
        val missingChannels = config.jobs
            .map { it.channel }
            .distinct()
            .filter { !config.channels.containsKey(it) }

        if (missingChannels.isNotEmpty()) {
            println("⚠️  Fehlende Channels: ${missingChannels.joinToString(", ")}")
            println("   Bitte in feedkrake.config unter [channels] hinzufügen!")
        }
    }

    private fun shouldExecute(job: JobConfig, now: LocalDateTime): Boolean {
        val lastRun = lastExecuted[job.name]
        val schedule = job.schedule.lowercase()

        return when {
            // Alle X Minuten
            schedule.startsWith("alle ") && schedule.endsWith("min") -> {
                val minutes = schedule.removePrefix("alle ").removeSuffix("min").trim().toIntOrNull() ?: return false
                lastRun == null || Duration.between(lastRun, now).toMinutes() >= minutes
            }

            // Täglich um HH:MM
            schedule.startsWith("täglich ") -> {
                val time = parseTime(schedule.removePrefix("täglich ").trim()) ?: return false
                val targetTime = now.toLocalDate().atTime(time)
                now.isAfter(targetTime) && (lastRun == null || lastRun.toLocalDate() != now.toLocalDate())
            }

            // Wochentag(e) um HH:MM
            else -> {
                val parsed = parseWeeklySchedule(schedule) ?: return false
                val days = parsed.first ?: return false
                val time = parsed.second ?: return false

                val isCorrectDay = now.dayOfWeek in days
                val targetTime = now.toLocalDate().atTime(time)
                isCorrectDay && now.isAfter(targetTime) &&
                    (lastRun == null || lastRun.toLocalDate() != now.toLocalDate())
            }
        }
    }

    private fun parseTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.split(":")
            LocalTime.of(parts[0].toInt(), parts.getOrNull(1)?.toInt() ?: 0)
        } catch (e: Exception) { null }
    }

    private fun parseWeeklySchedule(schedule: String): Pair<Set<DayOfWeek>?, LocalTime?>? {
        val parts = schedule.split(" ")
        if (parts.size != 2) return null

        val days = parseDays(parts[0])
        val time = parseTime(parts[1])
        return days to time
    }

    private fun parseDays(daysStr: String): Set<DayOfWeek>? {
        val dayMap = mapOf(
            "montags" to setOf(DayOfWeek.MONDAY),
            "dienstags" to setOf(DayOfWeek.TUESDAY),
            "mittwochs" to setOf(DayOfWeek.WEDNESDAY),
            "donnerstags" to setOf(DayOfWeek.THURSDAY),
            "freitags" to setOf(DayOfWeek.FRIDAY),
            "samstags" to setOf(DayOfWeek.SATURDAY),
            "sonntags" to setOf(DayOfWeek.SUNDAY),
            "mo" to setOf(DayOfWeek.MONDAY),
            "di" to setOf(DayOfWeek.TUESDAY),
            "mi" to setOf(DayOfWeek.WEDNESDAY),
            "do" to setOf(DayOfWeek.THURSDAY),
            "fr" to setOf(DayOfWeek.FRIDAY),
            "sa" to setOf(DayOfWeek.SATURDAY),
            "so" to setOf(DayOfWeek.SUNDAY)
        )

        // Einzelner Tag
        dayMap[daysStr.lowercase()]?.let { return it }

        // Mehrere Tage (mo,mi,fr)
        val days = daysStr.lowercase().split(",").mapNotNull { day ->
            dayMap[day.trim()]?.firstOrNull()
        }.toSet()

        return if (days.isNotEmpty()) days else null
    }

    private fun executeJob(job: JobConfig) {
        val timestamp = LocalDateTime.now(timezone).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        println("⏰ [$timestamp] ${job.name}")

        val webhookUrl = config.channels[job.channel]
        if (webhookUrl == null) {
            println("   ❌ Channel '${job.channel}' nicht konfiguriert!")
            return
        }

        val result = runModule(job)

        if (result != null) {
            val webhook = DiscordWebhook(webhookUrl)
            val sent = webhook.sendEmbed(
                title = job.name,
                description = result,
                color = getColorForModule(job.module)
            )
            println("   ${if (sent) "✅ Gesendet" else "❌ Senden fehlgeschlagen"}")
        } else {
            println("   ⚠️ Keine Daten")
        }
    }

    private fun runModule(job: JobConfig): String? {
        return when (job.module.lowercase()) {
            "heise.news" -> fetchHeise(HeiseFeed.ALLE, job.getIntOption("max", 10))
            "heise.security" -> fetchHeise(HeiseFeed.SECURITY, job.getIntOption("max", 10))
            "heise.developer" -> fetchHeise(HeiseFeed.DEVELOPER, job.getIntOption("max", 10))
            "handball.tabelle" -> "🤾 Handball Tabelle\n*(Noch nicht implementiert - benötigt Claude API)*"
            "handball.spiele" -> "🤾 Handball Spielplan\n*(Noch nicht implementiert - benötigt Claude API)*"
            "handball.ergebnisse" -> "🤾 Handball Ergebnisse\n*(Noch nicht implementiert - benötigt Claude API)*"
            "pubg.activity" -> "🎮 PUBG Activity\n*(Noch nicht implementiert)*"
            "pubg.weekly" -> "🎮 PUBG Wochenstatistik\n*(Noch nicht implementiert)*"
            else -> {
                println("   ⚠️ Unbekanntes Modul: ${job.module}")
                null
            }
        }
    }

    private fun fetchHeise(feed: HeiseFeed, maxArticles: Int): String? {
        val source = HeiseSource(HeiseModuleConfig(
            feed = feed,
            maxArticles = maxArticles,
            includeSponsored = false
        ))

        return source.fetchArticles().fold(
            onSuccess = { articles ->
                buildString {
                    appendLine("```")
                    articles.forEach { appendLine(it.discordFormat()) }
                    append("```")
                }
            },
            onFailure = { null }
        )
    }

    private fun getColorForModule(module: String): Int {
        return when {
            module.startsWith("heise") -> 0xE10019      // Heise Rot
            module.startsWith("handball") -> 0x00A86B  // Grün
            module.startsWith("pubg") -> 0xF2A900      // PUBG Gelb
            else -> 0x5865F2                            // Discord Blau
        }
    }
}
