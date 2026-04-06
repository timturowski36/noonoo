package de.aggregator.domain.port.output

import de.aggregator.domain.model.NewsArticle

interface NewsApiPort {
    suspend fun fetchArticles(url: String, sourceName: String): List<NewsArticle>
}
