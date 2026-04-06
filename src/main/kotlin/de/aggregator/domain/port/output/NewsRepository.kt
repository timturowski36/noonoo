package de.aggregator.domain.port.output

import de.aggregator.domain.model.NewsArticle

interface NewsRepository {
    fun saveArticles(articles: List<NewsArticle>)
    fun findLatestArticles(source: String, limit: Int): List<NewsArticle>
    fun findArticlesByKeywords(source: String, keywords: List<String>, limit: Int): List<NewsArticle>
}
