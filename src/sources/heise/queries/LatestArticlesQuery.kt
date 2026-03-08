package sources.heise.queries

import sources.heise.model.HeiseArticle
import java.time.Instant

data class LatestArticlesQueryConfig(
    val limit: Int = 5,
    val since: Instant? = null,
    val excludeSponsored: Boolean = true
)

class LatestArticlesQuery(
    private val config: LatestArticlesQueryConfig = LatestArticlesQueryConfig()
) : HeiseQuery {

    override fun execute(articles: List<HeiseArticle>): List<HeiseArticle> {
        return articles
            .filter { article ->
                val passesSponsored = !config.excludeSponsored || !article.isSponsored
                val passesSince = config.since == null || article.pubDate.isAfter(config.since)
                passesSponsored && passesSince
            }
            .sortedByDescending { it.pubDate }
            .take(config.limit)
    }
}
