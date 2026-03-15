package config.modules

import java.time.DayOfWeek

/**
 * Konfiguration für ein Bundesliga-Modul (1. oder 2. Liga).
 * Wird aus config/modules/bundesliga_1.conf oder bundesliga_2.conf geladen.
 */
data class BundesligaModuleConfig(
    val liga: String,
    val lieblingsverein: String,
    val channel: String,
    val minuteOffset: Int,
    val evenHoursOnly: Boolean,
    val oddHoursOnly: Boolean,
    /** Leere Liste = jeden Tag, sonst nur an diesen Wochentagen */
    val days: List<DayOfWeek>,
    /** Nur zu dieser Uhrzeit (z.B. 19 = 19:xx Uhr), null = jede Stunde */
    val hour: Int? = null
) {
    val ligaName: String get() = when (liga) {
        "bl1" -> "1. Bundesliga"
        "bl2" -> "2. Bundesliga"
        else -> liga
    }

    companion object {
        private val dayMap = mapOf(
            "mo" to DayOfWeek.MONDAY,    "montag" to DayOfWeek.MONDAY,
            "di" to DayOfWeek.TUESDAY,   "dienstag" to DayOfWeek.TUESDAY,
            "mi" to DayOfWeek.WEDNESDAY, "mittwoch" to DayOfWeek.WEDNESDAY,
            "do" to DayOfWeek.THURSDAY,  "donnerstag" to DayOfWeek.THURSDAY,
            "fr" to DayOfWeek.FRIDAY,    "freitag" to DayOfWeek.FRIDAY,
            "sa" to DayOfWeek.SATURDAY,  "samstag" to DayOfWeek.SATURDAY,
            "so" to DayOfWeek.SUNDAY,    "sonntag" to DayOfWeek.SUNDAY
        )

        fun load(path: String): BundesligaModuleConfig {
            val props = loadProps(path)
            val daysRaw = props["days"]?.trim() ?: ""
            val days = if (daysRaw.isBlank()) emptyList()
            else daysRaw.split(",").mapNotNull { dayMap[it.trim().lowercase()] }

            return BundesligaModuleConfig(
                liga = props["liga"] ?: "bl1",
                lieblingsverein = props["lieblingsverein"] ?: "",
                channel = props["channel"] ?: "allgemein",
                minuteOffset = props["minute_offset"]?.toIntOrNull() ?: 15,
                evenHoursOnly = props["even_hours_only"]?.toBoolean() ?: false,
                oddHoursOnly = props["odd_hours_only"]?.toBoolean() ?: false,
                days = days,
                hour = props["hour"]?.toIntOrNull()
            ).also {
                val daysStr = if (it.days.isEmpty()) "täglich" else it.days.joinToString(", ") { d -> d.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                println("✅ [${it.ligaName}] Lieblingsverein: '${it.lieblingsverein}', :${it.minuteOffset}, Channel: ${it.channel}, Tage: $daysStr")
            }
        }
    }
}
