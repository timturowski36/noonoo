package sources.heise.queries

import sources.heise.model.HeiseArticle
import java.time.Instant
import java.time.temporal.ChronoUnit

data class TimeRangeQueryConfig(
    val from: Instant? = null,
    val to: Instant? = null,
    val lastHours: Int? = null,
    val excludeSponsored: Boolean = true
)

class TimeRangeQuery(
    private val config: TimeRangeQueryConfig
) : HeiseQuery {

    override fun execute(articles: List<HeiseArticle>): List<HeiseArticle> {
        val effectiveFrom = config.from
            ?: config.lastHours?.let { Instant.now().minus(it.toLong(), ChronoUnit.HOURS) }

        val effectiveTo = config.to ?: Instant.now()

        return articles.filter { article ->
            val passesSponsored = !config.excludeSponsored || !article.isSponsored
            val passesFrom = effectiveFrom == null || article.pubDate.isAfter(effectiveFrom)
            val passesTo = article.pubDate.isBefore(effectiveTo)
            passesSponsored && passesFrom && passesTo
        }
    }
}
