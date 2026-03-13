package sources.tagesschau.config

import sources.tagesschau.model.TagesschauFeed

data class TagesschauModuleConfig(
    val feed: TagesschauFeed = TagesschauFeed.ALLE,
    val pollIntervalSeconds: Int = 300,
    val maxArticles: Int = 10
)
