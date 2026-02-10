package sources.pubg.queries

import domain.model.Query
import domain.model.QueryResult
import domain.model.QuerySettings
import sources.`pubg-api`.api.PubgApiClient
import sources.pubg.model.PlayerStats
import sources.pubg.model.PubgSettings
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class WeeklyStatsQuerySettings(
    val accountId: String = "",
    val weekStartHour: Int = 6,              // Woche startet um 6 Uhr
    val maxMatchesToCheck: Int = 100,        // Mehr Matches für eine Woche
    val timezone: String = "Europe/Berlin"
) : QuerySettings {

    /**
     * Berechnet den Start der aktuellen Woche (Montag um weekStartHour Uhr)
     */
    fun getWeekStartTime(): LocalDateTime {
        val zone = ZoneId.of(timezone)
        val now = LocalDateTime.now(zone)

        // Finde den letzten Montag (oder heute, wenn heute Montag ist)
        val lastMonday = if (now.dayOfWeek == DayOfWeek.MONDAY && now.hour >= weekStartHour) {
            now.toLocalDate()
        } else {
            now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }

        return lastMonday.atTime(weekStartHour, 0)
    }

    /**
     * Berechnet die Stunden seit Wochenstart
     */
    fun getHoursSinceWeekStart(): Int {
        val zone = ZoneId.of(timezone)
        val now = LocalDateTime.now(zone)
        val weekStart = getWeekStartTime()
        return ChronoUnit.HOURS.between(weekStart, now).toInt().coerceAtLeast(1)
    }
}

class WeeklyStatsQuery : Query<PubgSettings, WeeklyStatsQuerySettings, PlayerStats> {

    override val name = "PUBG Wochenbericht"
    override val defaultSettings = WeeklyStatsQuerySettings()

    override suspend fun execute(
        moduleSettings: PubgSettings,
        querySettings: WeeklyStatsQuerySettings
    ): QueryResult<PlayerStats> {
        if (querySettings.accountId.isBlank()) {
            return QueryResult.Error("Account-ID fehlt – führe zuerst AccountIdQuery aus")
        }

        val hoursSinceWeekStart = querySettings.getHoursSinceWeekStart()
        val weekStart = querySettings.getWeekStartTime()

        println("📅 Wochenbericht: Seit $weekStart ($hoursSinceWeekStart Stunden)")

        val apiClient = PubgApiClient(apiKey = moduleSettings.apiKey)
        val stats = apiClient.fetchRecentStats(
            platform = moduleSettings.platform,
            accountId = querySettings.accountId,
            hours = hoursSinceWeekStart,
            maxMatches = querySettings.maxMatchesToCheck
        ) ?: return QueryResult.Error("Weekly Stats nicht abrufbar")

        return QueryResult.Success(stats)
    }
}
