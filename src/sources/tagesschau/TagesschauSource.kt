package sources.tagesschau

import sources.tagesschau.api.TagesschauRssClient
import sources.tagesschau.config.TagesschauModuleConfig
import sources.tagesschau.model.TagesschauArticle
import sources.tagesschau.model.TagesschauFeed

/**
 * Tagesschau RSS Module mit vorkonfigurierten Abfragen.
 */
class TagesschauSource(
    private val config: TagesschauModuleConfig = TagesschauModuleConfig()
) {
    private val client = TagesschauRssClient()

    fun fetchArticles(): Result<List<TagesschauArticle>> {
        return client.fetchArticles(config.feed).map { articles ->
            articles.take(config.maxArticles)
        }
    }

    /**
     * Sucht nach Artikeln mit den angegebenen Keywords.
     */
    fun searchKeywords(vararg keywords: String, maxResults: Int = 10): Result<List<TagesschauArticle>> {
        return fetchArticles().map { articles ->
            articles.filter { article ->
                keywords.any { keyword -> article.containsKeyword(keyword) }
            }.take(maxResults)
        }
    }

    companion object {
        // ═══════════════════════════════════════════════════════════════════════
        // Vorkonfigurierte Module nach Ressort
        // ═══════════════════════════════════════════════════════════════════════

        /** Alle Tagesschau News */
        fun news(max: Int = 10) = TagesschauSource(TagesschauModuleConfig(
            feed = TagesschauFeed.ALLE,
            maxArticles = max
        ))

        /** Inland-Nachrichten */
        fun inland(max: Int = 10) = TagesschauSource(TagesschauModuleConfig(
            feed = TagesschauFeed.INLAND,
            maxArticles = max
        ))

        /** Ausland-Nachrichten */
        fun ausland(max: Int = 10) = TagesschauSource(TagesschauModuleConfig(
            feed = TagesschauFeed.AUSLAND,
            maxArticles = max
        ))

        /** Wirtschaft */
        fun wirtschaft(max: Int = 10) = TagesschauSource(TagesschauModuleConfig(
            feed = TagesschauFeed.WIRTSCHAFT,
            maxArticles = max
        ))

        /** Sport */
        fun sport(max: Int = 10) = TagesschauSource(TagesschauModuleConfig(
            feed = TagesschauFeed.SPORT,
            maxArticles = max
        ))

        /** Investigativ-Recherchen */
        fun investigativ(max: Int = 10) = TagesschauSource(TagesschauModuleConfig(
            feed = TagesschauFeed.INVESTIGATIV,
            maxArticles = max
        ))

        /** Wissen & Forschung */
        fun wissen(max: Int = 10) = TagesschauSource(TagesschauModuleConfig(
            feed = TagesschauFeed.WISSEN,
            maxArticles = max
        ))

        /** Faktenfinder (Faktencheck) */
        fun faktenfinder(max: Int = 10) = TagesschauSource(TagesschauModuleConfig(
            feed = TagesschauFeed.FAKTENFINDER,
            maxArticles = max
        ))

        // ═══════════════════════════════════════════════════════════════════════
        // Vorkonfigurierte Keyword-Suchen
        // ═══════════════════════════════════════════════════════════════════════

        /** Politik-News */
        fun politik(max: Int = 10) = news(50).searchKeywords(
            "Bundestag", "Bundesregierung", "Kanzler", "Minister", "Koalition", "Partei",
            maxResults = max
        )

        /** Ukraine-Konflikt */
        fun ukraine(max: Int = 10) = news(50).searchKeywords(
            "Ukraine", "Kiew", "Selenskyj", "Russland", "Putin",
            maxResults = max
        )

        /** Klima & Umwelt */
        fun klima(max: Int = 10) = news(50).searchKeywords(
            "Klima", "Umwelt", "CO2", "Energie", "Erneuerbar", "Klimaschutz",
            maxResults = max
        )

        /** Corona / Gesundheit */
        fun gesundheit(max: Int = 10) = news(50).searchKeywords(
            "Corona", "Gesundheit", "Impfung", "Krankenhaus", "RKI",
            maxResults = max
        )
    }
}
