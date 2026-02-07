package sources.heise.config

import sources.heise.model.HeiseFeed

data class HeiseModuleConfig(
    val feed: HeiseFeed = HeiseFeed.ALLE,
    val pollIntervalSeconds: Int = 300,
    val maxArticles: Int = 10,
    val includeSponsored: Boolean = false,
    val language: String = "de"
)
