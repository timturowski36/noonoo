package scheduler.tasks

import scheduler.model.*
import sources.heise.HeiseSource
import sources.heise.config.HeiseModuleConfig
import sources.heise.model.HeiseFeed

/**
 * Führt Tasks basierend auf ihrem Typ aus.
 */
class TaskExecutor {

    fun execute(task: ScheduledTask): TaskResult {
        return when (task.type) {
            TaskType.HEISE_NEWS -> executeHeiseNews(task)
            TaskType.HEISE_SECURITY -> executeHeiseSecurity(task)
            TaskType.HEISE_DEVELOPER -> executeHeiseDeveloper(task)
            TaskType.HANDBALL_SCHEDULE -> executeHandballSchedule(task)
            TaskType.HANDBALL_RESULTS -> executeHandballResults(task)
            TaskType.HANDBALL_TABLE -> executeHandballTable(task)
            TaskType.PUBG_ACTIVITY -> executePubgActivity(task)
            TaskType.PUBG_WEEKLY_STATS -> executePubgWeeklyStats(task)
            TaskType.CUSTOM -> TaskResult(task.id, false, "Custom tasks not implemented")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Heise Tasks
    // ─────────────────────────────────────────────────────────────────────────

    private fun executeHeiseNews(task: ScheduledTask): TaskResult {
        return executeHeiseFeed(task, HeiseFeed.ALLE, "📰 Heise News")
    }

    private fun executeHeiseSecurity(task: ScheduledTask): TaskResult {
        return executeHeiseFeed(task, HeiseFeed.SECURITY, "🔒 Heise Security")
    }

    private fun executeHeiseDeveloper(task: ScheduledTask): TaskResult {
        return executeHeiseFeed(task, HeiseFeed.DEVELOPER, "💻 Heise Developer")
    }

    private fun executeHeiseFeed(task: ScheduledTask, feed: HeiseFeed, title: String): TaskResult {
        val maxArticles = task.config["maxArticles"]?.toIntOrNull() ?: 10

        val source = HeiseSource(HeiseModuleConfig(
            feed = feed,
            maxArticles = maxArticles,
            includeSponsored = false
        ))

        return source.fetchArticles().fold(
            onSuccess = { articles ->
                val message = buildString {
                    appendLine("```")
                    articles.forEach { appendLine(it.discordFormat()) }
                    append("```")
                }
                TaskResult(task.id, true, "${articles.size} Artikel geladen", message)
            },
            onFailure = { error ->
                TaskResult(task.id, false, error.message)
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handball Tasks
    // ─────────────────────────────────────────────────────────────────────────

    private fun executeHandballSchedule(task: ScheduledTask): TaskResult {
        // TODO: Implementierung mit Claude API + Cache
        return TaskResult(task.id, false, "Handball Schedule: Benötigt Claude API Integration")
    }

    private fun executeHandballResults(task: ScheduledTask): TaskResult {
        // TODO: Implementierung mit Claude API + Cache
        return TaskResult(task.id, false, "Handball Results: Benötigt Claude API Integration")
    }

    private fun executeHandballTable(task: ScheduledTask): TaskResult {
        // TODO: Implementierung mit Claude API + Cache
        return TaskResult(task.id, false, "Handball Table: Benötigt Claude API Integration")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBG Tasks
    // ─────────────────────────────────────────────────────────────────────────

    private fun executePubgActivity(task: ScheduledTask): TaskResult {
        // TODO: Implementierung mit PUBG API
        // Prüft ob in den letzten X Minuten Matches gespielt wurden
        val players = task.config["players"]?.split(",") ?: emptyList()
        return TaskResult(task.id, false, "PUBG Activity: Benötigt PUBG API Integration (Players: $players)")
    }

    private fun executePubgWeeklyStats(task: ScheduledTask): TaskResult {
        // TODO: Implementierung mit PUBG API
        return TaskResult(task.id, false, "PUBG Weekly Stats: Benötigt PUBG API Integration")
    }
}
