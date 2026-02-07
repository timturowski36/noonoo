package sources.heise.queries

import sources.heise.model.HeiseArticle

data class KeywordSearchQueryConfig(
    val keywords: List<String>,
    val matchAll: Boolean = false,
    val excludeSponsored: Boolean = true
)

class KeywordSearchQuery(
    private val config: KeywordSearchQueryConfig
) : HeiseQuery {

    override fun execute(articles: List<HeiseArticle>): List<HeiseArticle> {
        return articles.filter { article ->
            val passesSponsored = !config.excludeSponsored || !article.isSponsored
            val passesKeywords = if (config.matchAll) {
                config.keywords.all { keyword -> article.containsKeyword(keyword) }
            } else {
                config.keywords.any { keyword -> article.containsKeyword(keyword) }
            }
            passesSponsored && passesKeywords
        }
    }
}
