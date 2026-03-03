import sources.heise.HeiseSource
import sources.heise.config.HeiseModuleConfig
import sources.heise.model.HeiseFeed
import sources.heise.queries.KeywordSearchQuery
import sources.heise.queries.KeywordSearchQueryConfig
import sources.heise.queries.LatestArticlesQuery
import sources.heise.queries.LatestArticlesQueryConfig

fun main(args: Array<String>) {
    // Falls Argument übergeben, direkt ausführen
    if (args.isNotEmpty()) {
        runModule(args[0])
        return
    }

    // Interaktives Menü
    while (true) {
        printMenu()
        val input = readlnOrNull()?.trim() ?: break

        if (input == "0" || input.equals("exit", ignoreCase = true)) {
            println("Auf Wiedersehen!")
            break
        }

        runModule(input)
        println("\n[Enter drücken für Menü...]")
        readlnOrNull()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Menü
// ══════════════════════════════════════════════════════════════════════════════

fun printMenu() {
    println("""

    ╔═══════════════════════════════════════╗
    ║        🐙 FeedKrake - Menü            ║
    ╠═══════════════════════════════════════╣
    ║  1. Heise RSS Feed                    ║
    ║  2. Heise Security                    ║
    ║  3. Heise Developer                   ║
    ║  4. Heise Keyword-Suche               ║
    ║                                       ║
    ║  0. Beenden                           ║
    ╚═══════════════════════════════════════╝

    Auswahl: """.trimIndent())
}

fun runModule(input: String) {
    when (input.lowercase()) {
        "1", "heise" -> testHeiseOnline()
        "2", "security" -> testHeiseSecurity()
        "3", "developer" -> testHeiseDeveloper()
        "4", "search" -> testHeiseSearch()
        else -> println("❌ Unbekannte Auswahl: $input")
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Heise Module
// ══════════════════════════════════════════════════════════════════════════════

fun testHeiseOnline() {
    println("\n── 📰 Heise Online ────────────────────")
    val source = HeiseSource(HeiseModuleConfig(
        feed = HeiseFeed.ALLE,
        maxArticles = 10,
        includeSponsored = false
    ))

    source.fetchArticles().fold(
        onSuccess = { articles ->
            println("📡 ${HeiseFeed.ALLE.displayName} - ${articles.size} Artikel\n")
            articles.forEach { println(it.discordFormat()) }
        },
        onFailure = { println("❌ Fehler: ${it.message}") }
    )
}

fun testHeiseSecurity() {
    println("\n── 🔒 Heise Security ──────────────────")
    val source = HeiseSource(HeiseModuleConfig(
        feed = HeiseFeed.SECURITY,
        maxArticles = 10
    ))

    source.fetchArticles().fold(
        onSuccess = { articles ->
            println("🔒 ${HeiseFeed.SECURITY.displayName} - ${articles.size} Artikel\n")
            articles.forEach { println(it.discordFormat()) }
        },
        onFailure = { println("❌ Fehler: ${it.message}") }
    )
}

fun testHeiseDeveloper() {
    println("\n── 💻 Heise Developer ─────────────────")
    val source = HeiseSource(HeiseModuleConfig(
        feed = HeiseFeed.DEVELOPER,
        maxArticles = 10
    ))

    source.fetchArticles().fold(
        onSuccess = { articles ->
            println("💻 ${HeiseFeed.DEVELOPER.displayName} - ${articles.size} Artikel\n")
            articles.forEach { println(it.discordFormat()) }
        },
        onFailure = { println("❌ Fehler: ${it.message}") }
    )
}

fun testHeiseSearch() {
    print("\n🔍 Suchbegriff eingeben: ")
    val keyword = readlnOrNull()?.trim() ?: return

    val source = HeiseSource(HeiseModuleConfig(
        feed = HeiseFeed.ALLE,
        maxArticles = 50
    ))

    source.executeQuery(KeywordSearchQuery(KeywordSearchQueryConfig(keywords = listOf(keyword)))).fold(
        onSuccess = { articles ->
            println("\n🔍 ${articles.size} Artikel mit '$keyword' gefunden:\n")
            articles.take(10).forEach { println(it.discordFormat()) }
            if (articles.size > 10) println("\n... und ${articles.size - 10} weitere")
        },
        onFailure = { println("❌ Fehler: ${it.message}") }
    )
}
