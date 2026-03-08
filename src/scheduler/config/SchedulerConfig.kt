package scheduler.config

import scheduler.model.ScheduledTask
import scheduler.model.TaskSchedule
import scheduler.model.TaskType
import java.io.File
import java.time.DayOfWeek

/**
 * Konfiguration für den Scheduler.
 */
data class SchedulerConfig(
    val tasks: List<ScheduledTask>,
    val checkIntervalSeconds: Int = 60,
    val timezone: String = "Europe/Berlin"
) {
    companion object {
        private const val CONFIG_FILE = "src/scheduler/config/scheduler_config.txt"

        /**
         * Lädt die Konfiguration aus der Datei oder erstellt eine Beispielkonfiguration.
         */
        fun load(): SchedulerConfig {
            val file = File(CONFIG_FILE)
            if (!file.exists()) {
                createDefaultConfig(file)
                println("📝 [Scheduler] Beispielkonfiguration erstellt: $CONFIG_FILE")
                println("   Bitte anpassen und neu starten!")
            }
            return parse(file.readText())
        }

        private fun createDefaultConfig(file: File) {
            file.parentFile?.mkdirs()
            file.writeText("""
                # ═══════════════════════════════════════════════════════════════
                # FeedKrake Scheduler Konfiguration
                # ═══════════════════════════════════════════════════════════════
                #
                # Format für Tasks:
                # TASK:<id>|<name>|<type>|<schedule>|<webhook>|<enabled>|<config>
                #
                # Schedule-Formate:
                #   daily:HH:MM           - Täglich um HH:MM
                #   weekly:MO,DI,MI:HH:MM - An bestimmten Tagen um HH:MM
                #   interval:MINUTEN      - Alle X Minuten
                #   startup               - Einmalig beim Start
                #
                # Wochentage: MO, DI, MI, DO, FR, SA, SO
                #
                # Task-Typen:
                #   HEISE_NEWS, HEISE_SECURITY, HEISE_DEVELOPER
                #   HANDBALL_SCHEDULE, HANDBALL_RESULTS, HANDBALL_TABLE
                #   PUBG_ACTIVITY, PUBG_WEEKLY_STATS
                #
                # ═══════════════════════════════════════════════════════════════

                # Globale Einstellungen
                CHECK_INTERVAL=60
                TIMEZONE=Europe/Berlin

                # ─── Heise News ───────────────────────────────────────────────
                TASK:heise-daily|Heise Tägliche News|HEISE_NEWS|daily:08:00|https://discord.com/api/webhooks/YOUR_WEBHOOK|true|maxArticles=10

                # ─── Handball ─────────────────────────────────────────────────
                TASK:handball-monday|Handball Wochenübersicht|HANDBALL_TABLE|weekly:MO:18:00|https://discord.com/api/webhooks/YOUR_WEBHOOK|true|

                # ─── PUBG Activity ────────────────────────────────────────────
                # Prüft alle 30 Minuten ob jemand PUBG gespielt hat
                TASK:pubg-activity|PUBG Aktivität|PUBG_ACTIVITY|interval:30|https://discord.com/api/webhooks/YOUR_WEBHOOK|true|players=Player1,Player2

                # ─── PUBG Wochenstatistik ─────────────────────────────────────
                TASK:pubg-weekly|PUBG Wochenstatistik|PUBG_WEEKLY_STATS|weekly:SO:20:00|https://discord.com/api/webhooks/YOUR_WEBHOOK|true|

            """.trimIndent())
        }

        private fun parse(content: String): SchedulerConfig {
            val tasks = mutableListOf<ScheduledTask>()
            var checkInterval = 60
            var timezone = "Europe/Berlin"

            content.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { line ->
                    when {
                        line.startsWith("CHECK_INTERVAL=") -> {
                            checkInterval = line.substringAfter("=").toIntOrNull() ?: 60
                        }
                        line.startsWith("TIMEZONE=") -> {
                            timezone = line.substringAfter("=")
                        }
                        line.startsWith("TASK:") -> {
                            parseTask(line.substringAfter("TASK:"))?.let { tasks.add(it) }
                        }
                    }
                }

            return SchedulerConfig(tasks, checkInterval, timezone)
        }

        private fun parseTask(line: String): ScheduledTask? {
            val parts = line.split("|")
            if (parts.size < 5) {
                println("⚠️ [Config] Ungültige Task-Zeile: $line")
                return null
            }

            val id = parts[0]
            val name = parts[1]
            val type = try { TaskType.valueOf(parts[2]) } catch (e: Exception) {
                println("⚠️ [Config] Unbekannter Task-Typ: ${parts[2]}")
                return null
            }
            val schedule = parseSchedule(parts[3]) ?: return null
            val webhook = parts[4]
            val enabled = parts.getOrNull(5)?.toBoolean() ?: true
            val config = parts.getOrNull(6)?.let { parseConfig(it) } ?: emptyMap()

            return ScheduledTask(id, name, type, schedule, webhook, enabled, config)
        }

        private fun parseSchedule(schedule: String): TaskSchedule? {
            return when {
                schedule.startsWith("daily:") -> {
                    val time = schedule.substringAfter("daily:")
                    val (hour, minute) = time.split(":").map { it.toInt() }
                    TaskSchedule.daily(hour, minute)
                }
                schedule.startsWith("weekly:") -> {
                    val parts = schedule.substringAfter("weekly:").split(":")
                    val days = parts[0].split(",").mapNotNull { parseDayOfWeek(it) }.toSet()
                    val hour = parts[1].toInt()
                    val minute = parts.getOrNull(2)?.toInt() ?: 0
                    TaskSchedule.Weekly(days, java.time.LocalTime.of(hour, minute))
                }
                schedule.startsWith("interval:") -> {
                    val minutes = schedule.substringAfter("interval:").toInt()
                    TaskSchedule.everyMinutes(minutes)
                }
                schedule == "startup" -> TaskSchedule.OnStartup
                else -> {
                    println("⚠️ [Config] Unbekanntes Schedule-Format: $schedule")
                    null
                }
            }
        }

        private fun parseDayOfWeek(day: String): DayOfWeek? {
            return when (day.uppercase()) {
                "MO", "MONDAY" -> DayOfWeek.MONDAY
                "DI", "TU", "TUESDAY" -> DayOfWeek.TUESDAY
                "MI", "WE", "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                "DO", "TH", "THURSDAY" -> DayOfWeek.THURSDAY
                "FR", "FRIDAY" -> DayOfWeek.FRIDAY
                "SA", "SATURDAY" -> DayOfWeek.SATURDAY
                "SO", "SU", "SUNDAY" -> DayOfWeek.SUNDAY
                else -> null
            }
        }

        private fun parseConfig(configStr: String): Map<String, String> {
            if (configStr.isBlank()) return emptyMap()
            return configStr.split(",")
                .mapNotNull { pair ->
                    val kv = pair.split("=")
                    if (kv.size == 2) kv[0] to kv[1] else null
                }
                .toMap()
        }
    }
}
