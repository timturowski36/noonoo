package sources.heise

import sources.heise.api.HeiseRssClient
import sources.heise.config.HeiseModuleConfig
import sources.heise.model.HeiseArticle
import sources.heise.model.HeiseFeed
import sources.heise.queries.HeiseQuery
import sources.heise.queries.KeywordSearchQuery
import sources.heise.queries.KeywordSearchQueryConfig

/**
 * Heise RSS Module mit vorkonfigurierten Abfragen.
 */
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

    /**
     * Sucht nach Artikeln mit den angegebenen Keywords.
     */
    fun searchKeywords(vararg keywords: String, maxResults: Int = 10): Result<List<HeiseArticle>> {
        return executeQuery(KeywordSearchQuery(
            KeywordSearchQueryConfig(keywords = keywords.toList())
        )).map { it.take(maxResults) }
    }

    companion object {
        // ═══════════════════════════════════════════════════════════════════════
        // Vorkonfigurierte Module
        // ═══════════════════════════════════════════════════════════════════════

        /** Alle Heise News (Standard) */
        fun news(max: Int = 10) = HeiseSource(HeiseModuleConfig(
            feed = HeiseFeed.ALLE,
            maxArticles = max
        ))

        /** Security-Alerts */
        fun security(max: Int = 10) = HeiseSource(HeiseModuleConfig(
            feed = HeiseFeed.SECURITY,
            maxArticles = max
        ))

        /** Developer News */
        fun developer(max: Int = 10) = HeiseSource(HeiseModuleConfig(
            feed = HeiseFeed.DEVELOPER,
            maxArticles = max
        ))

        /** c't Magazin */
        fun ct(max: Int = 10) = HeiseSource(HeiseModuleConfig(
            feed = HeiseFeed.CT,
            maxArticles = max
        ))

        /** iX Magazin */
        fun ix(max: Int = 10) = HeiseSource(HeiseModuleConfig(
            feed = HeiseFeed.IX,
            maxArticles = max
        ))

        /** Telepolis */
        fun telepolis(max: Int = 10) = HeiseSource(HeiseModuleConfig(
            feed = HeiseFeed.TELEPOLIS,
            maxArticles = max
        ))

        // ═══════════════════════════════════════════════════════════════════════
        // Vorkonfigurierte Keyword-Suchen
        // ═══════════════════════════════════════════════════════════════════════

        /** KI / AI News */
        fun ki(max: Int = 10) = news(50).searchKeywords(
            "KI", "AI", "Künstliche Intelligenz", "ChatGPT", "Claude", "LLM", "Machine Learning",
            maxResults = max
        )

        /** Gaming News */
        fun gaming(max: Int = 10) = news(50).searchKeywords(
            "Gaming", "Spiel", "PlayStation", "Xbox", "Nintendo", "Steam", "GPU",
            maxResults = max
        )

        /** Apple News */
        fun apple(max: Int = 10) = news(50).searchKeywords(
            "Apple", "iPhone", "iPad", "Mac", "iOS", "macOS",
            maxResults = max
        )

        /** Linux News */
        fun linux(max: Int = 10) = news(50).searchKeywords(
            "Linux", "Ubuntu", "Debian", "Kernel", "Open Source",
            maxResults = max
        )

        /** Microsoft / Windows News */
        fun microsoft(max: Int = 10) = news(50).searchKeywords(
            "Microsoft", "Windows", "Azure", "Office", "Edge",
            maxResults = max
        )

        /** Datenschutz / Privacy News */
        fun privacy(max: Int = 10) = news(50).searchKeywords(
            "Datenschutz", "DSGVO", "Privacy", "Überwachung", "Tracking",
            maxResults = max
        )

        /** Elektroauto / E-Mobility News */
        fun emobility(max: Int = 10) = news(50).searchKeywords(
            "Elektroauto", "E-Auto", "Tesla", "BEV", "Ladestation", "Batterie",
            maxResults = max
        )
    }
}
