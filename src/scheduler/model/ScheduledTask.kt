package scheduler.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Definition eines geplanten Tasks.
 */
data class ScheduledTask(
    val id: String,
    val name: String,
    val type: TaskType,
    val schedule: TaskSchedule,
    val discordWebhook: String,
    val enabled: Boolean = true,
    val config: Map<String, String> = emptyMap()
)

/**
 * Verfügbare Task-Typen.
 */
enum class TaskType {
    HEISE_NEWS,           // Heise RSS News
    HEISE_SECURITY,       // Heise Security News
    HEISE_DEVELOPER,      // Heise Developer News
    HANDBALL_SCHEDULE,    // Handball Spielplan
    HANDBALL_RESULTS,     // Handball Ergebnisse
    HANDBALL_TABLE,       // Handball Tabelle
    PUBG_ACTIVITY,        // PUBG Activity Check (event-basiert)
    PUBG_WEEKLY_STATS,    // PUBG Wochenstatistik
    CUSTOM                // Benutzerdefiniert
}

/**
 * Zeitplan für einen Task.
 */
sealed class TaskSchedule {
    /**
     * Täglich zu einer bestimmten Uhrzeit.
     */
    data class Daily(val time: LocalTime) : TaskSchedule()

    /**
     * An bestimmten Wochentagen zu einer Uhrzeit.
     */
    data class Weekly(val days: Set<DayOfWeek>, val time: LocalTime) : TaskSchedule()

    /**
     * Alle X Minuten (für Polling wie PUBG-Activity).
     */
    data class Interval(val minutes: Int) : TaskSchedule()

    /**
     * Einmalig beim Start.
     */
    object OnStartup : TaskSchedule()

    companion object {
        fun daily(hour: Int, minute: Int = 0) = Daily(LocalTime.of(hour, minute))

        fun weekly(vararg days: DayOfWeek, hour: Int, minute: Int = 0) =
            Weekly(days.toSet(), LocalTime.of(hour, minute))

        fun everyMinutes(minutes: Int) = Interval(minutes)
    }
}

/**
 * Ergebnis einer Task-Ausführung.
 */
data class TaskResult(
    val taskId: String,
    val success: Boolean,
    val message: String?,
    val discordMessage: String? = null
)
