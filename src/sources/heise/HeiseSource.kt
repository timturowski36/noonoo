package sources.heise

import sources.heise.api.HeiseRssClient
import sources.heise.config.HeiseModuleConfig
import sources.heise.model.HeiseArticle
import sources.heise.queries.HeiseQuery

class HeiseSource(
    private val config: HeiseModuleConfig = HeiseModuleConfig()
) {
    private val client = HeiseRssClient()

    fun fetchArticles(): Result<List<HeiseArticle>> {
        return client.fetchArticles(config.feed).map { articles ->
            val filtered = if (config.includeSponsored) {
                articles
            } else {
                articles.filter { !it.isSponsored }
            }
            filtered.take(config.maxArticles)
        }
    }

    fun executeQuery(query: HeiseQuery): Result<List<HeiseArticle>> {
        return fetchArticles().map { articles ->
            query.execute(articles)
        }
    }
}
