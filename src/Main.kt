import sources.heise.HeiseSource
import sources.heise.config.HeiseModuleConfig
import sources.heise.model.HeiseFeed
import sources.heise.queries.KeywordSearchQuery
import sources.heise.queries.KeywordSearchQueryConfig
import sources.heise.queries.LatestArticlesQuery
import sources.heise.queries.LatestArticlesQueryConfig

fun main() {
    testHeiseFeed()
}

// ══════════════════════════════════════════════════════════════════════════════
// Heise RSS Feed Test
// ══════════════════════════════════════════════════════════════════════════════

fun testHeiseFeed() {
    println("═══════════════════════════════════════")
    println("   📰 Heise RSS Feed Test")
    println("═══════════════════════════════════════")

    // 1. Heise Online (Alle News)
    println("\n── Heise Online (Alle News) ───────────")
    val heiseOnline = HeiseSource(HeiseModuleConfig(
        feed = HeiseFeed.ALLE,
        maxArticles = 10,
        includeSponsored = false
    ))

    heiseOnline.fetchArticles().fold(
        onSuccess = { articles ->
            println("📡 **${HeiseFeed.ALLE.displayName}** - ${articles.size} Artikel")
            println("```")
            articles.forEach { println(it.discordFormat()) }
            println("```")
        },
        onFailure = { error ->
            println("❌ Fehler: ${error.message}")
        }
    )

    // 2. Security News
    println("\n── Heise Security ─────────────────────")
    val heiseSecurity = HeiseSource(HeiseModuleConfig(
        feed = HeiseFeed.SECURITY,
        maxArticles = 5
    ))

    heiseSecurity.fetchArticles().fold(
        onSuccess = { articles ->
            println("🔒 **${HeiseFeed.SECURITY.displayName}** - ${articles.size} Artikel")
            println("```")
            articles.forEach { println(it.discordFormat()) }
            println("```")
        },
        onFailure = { error ->
            println("❌ Fehler: ${error.message}")
        }
    )

    // 3. Developer News
    println("\n── Heise Developer ────────────────────")
    val heiseDev = HeiseSource(HeiseModuleConfig(
        feed = HeiseFeed.DEVELOPER,
        maxArticles = 5
    ))

    heiseDev.fetchArticles().fold(
        onSuccess = { articles ->
            println("💻 **${HeiseFeed.DEVELOPER.displayName}** - ${articles.size} Artikel")
            println("```")
            articles.forEach { println(it.discordFormat()) }
            println("```")
        },
        onFailure = { error ->
            println("❌ Fehler: ${error.message}")
        }
    )

    // 4. Keyword-Suche
    println("\n── Keyword-Suche: 'KI' ────────────────")
    val searchSource = HeiseSource(HeiseModuleConfig(
        feed = HeiseFeed.ALLE,
        maxArticles = 50
    ))

    searchSource.executeQuery(KeywordSearchQuery(KeywordSearchQueryConfig(keywords = listOf("KI")))).fold(
        onSuccess = { articles ->
            println("🔍 Gefunden: ${articles.size} Artikel mit 'KI'")
            println("```")
            articles.take(5).forEach { println(it.discordFormat()) }
            if (articles.size > 5) println("... und ${articles.size - 5} weitere")
            println("```")
        },
        onFailure = { error ->
            println("❌ Fehler: ${error.message}")
        }
    )

    // 5. Detaillierte Ansicht
    println("\n── Detaillierte Artikel ───────────────")
    heiseOnline.executeQuery(LatestArticlesQuery(LatestArticlesQueryConfig(limit = 3))).fold(
        onSuccess = { articles ->
            articles.forEach {
                println(it.discordFormatDetailed())
                println()
            }
        },
        onFailure = { error ->
            println("❌ Fehler: ${error.message}")
        }
    )

    println("✅ Test abgeschlossen!")
}
