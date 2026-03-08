package scheduler

import scheduler.config.SchedulerConfig
import scheduler.discord.DiscordWebhook
import scheduler.model.*
import scheduler.tasks.TaskExecutor
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Haupt-Scheduler für FeedKrake.
 * Führt Tasks basierend auf ihrer Konfiguration aus.
 */
class Scheduler(private val config: SchedulerConfig) {

    private val timezone = ZoneId.of(config.timezone)
    private val lastExecuted = ConcurrentHashMap<String, LocalDateTime>()
    private val taskExecutor = TaskExecutor()
    private var running = false

    /**
     * Startet den Scheduler im Dauerbetrieb.
     */
    fun start() {
        println("""

            ╔═══════════════════════════════════════════════════════════════╗
            ║           🐙 FeedKrake Scheduler gestartet                    ║
            ╠═══════════════════════════════════════════════════════════════╣
            ║  Tasks: ${config.tasks.count { it.enabled }.toString().padEnd(3)} aktiv                                          ║
            ║  Check-Interval: ${config.checkIntervalSeconds.toString().padEnd(3)} Sekunden                              ║
            ║  Timezone: ${config.timezone.padEnd(20)}                        ║
            ╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent())

        printTaskOverview()

        // Startup-Tasks ausführen
        executeStartupTasks()

        running = true
        while (running) {
            checkAndExecuteTasks()
            Thread.sleep(config.checkIntervalSeconds * 1000L)
        }
    }

    /**
     * Stoppt den Scheduler.
     */
    fun stop() {
        running = false
        println("\n🛑 [Scheduler] Wird gestoppt...")
    }

    /**
     * Führt einen einzelnen Task sofort aus (für Tests).
     */
    fun executeNow(taskId: String): TaskResult? {
        val task = config.tasks.find { it.id == taskId }
        if (task == null) {
            println("❌ Task nicht gefunden: $taskId")
            return null
        }
        return executeTask(task)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Interne Methoden
    // ─────────────────────────────────────────────────────────────────────────

    private fun printTaskOverview() {
        println("\n📋 Konfigurierte Tasks:")
        println("─".repeat(60))
        config.tasks.forEach { task ->
            val status = if (task.enabled) "✅" else "❌"
            val scheduleStr = formatSchedule(task.schedule)
            println("$status ${task.name.padEnd(30)} | $scheduleStr")
        }
        println("─".repeat(60))
        println()
    }

    private fun formatSchedule(schedule: TaskSchedule): String {
        return when (schedule) {
            is TaskSchedule.Daily -> "Täglich um ${schedule.time}"
            is TaskSchedule.Weekly -> {
                val days = schedule.days.joinToString(",") { it.name.take(2) }
                "$days um ${schedule.time}"
            }
            is TaskSchedule.Interval -> "Alle ${schedule.minutes} Min"
            is TaskSchedule.OnStartup -> "Beim Start"
        }
    }

    private fun executeStartupTasks() {
        config.tasks
            .filter { it.enabled && it.schedule is TaskSchedule.OnStartup }
            .forEach { task ->
                println("🚀 [Startup] Führe aus: ${task.name}")
                executeTask(task)
            }
    }

    private fun checkAndExecuteTasks() {
        val now = LocalDateTime.now(timezone)

        config.tasks
            .filter { it.enabled && it.schedule !is TaskSchedule.OnStartup }
            .forEach { task ->
                if (shouldExecute(task, now)) {
                    executeTask(task)
                    lastExecuted[task.id] = now
                }
            }
    }

    private fun shouldExecute(task: ScheduledTask, now: LocalDateTime): Boolean {
        val lastRun = lastExecuted[task.id]

        return when (val schedule = task.schedule) {
            is TaskSchedule.Daily -> {
                val targetTime = now.toLocalDate().atTime(schedule.time)
                now.isAfter(targetTime) && (lastRun == null || lastRun.toLocalDate() != now.toLocalDate())
            }
            is TaskSchedule.Weekly -> {
                val isCorrectDay = now.dayOfWeek in schedule.days
                val targetTime = now.toLocalDate().atTime(schedule.time)
                isCorrectDay && now.isAfter(targetTime) &&
                    (lastRun == null || lastRun.toLocalDate() != now.toLocalDate())
            }
            is TaskSchedule.Interval -> {
                lastRun == null || Duration.between(lastRun, now).toMinutes() >= schedule.minutes
            }
            is TaskSchedule.OnStartup -> false // Bereits behandelt
        }
    }

    private fun executeTask(task: ScheduledTask): TaskResult {
        val now = LocalDateTime.now(timezone).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        println("⏰ [$now] Führe aus: ${task.name}")

        return try {
            val result = taskExecutor.execute(task)

            if (result.success && result.discordMessage != null) {
                val webhook = DiscordWebhook(task.discordWebhook)
                webhook.sendEmbed(
                    title = task.name,
                    description = result.discordMessage,
                    color = DiscordWebhook.COLOR_BLUE
                )
            }

            if (result.success) {
                println("   ✅ Erfolgreich")
            } else {
                println("   ❌ Fehlgeschlagen: ${result.message}")
            }

            result
        } catch (e: Exception) {
            println("   ❌ Exception: ${e.message}")
            TaskResult(task.id, false, e.message)
        }
    }
}
