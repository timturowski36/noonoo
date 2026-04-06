package de.feedkrake.domain.port.output

import de.feedkrake.domain.model.NewsArticle

interface NewsApiPort {
    suspend fun fetchArticles(url: String, sourceName: String): List<NewsArticle>
}
