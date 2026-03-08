package sources.heise.queries

import sources.heise.model.HeiseArticle

interface HeiseQuery {
    fun execute(articles: List<HeiseArticle>): List<HeiseArticle>
}
