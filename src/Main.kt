import scheduler.Scheduler
import scheduler.config.SchedulerConfig
import sources.heise.HeiseSource
import sources.heise.config.HeiseModuleConfig
import sources.heise.model.HeiseFeed
import sources.heise.queries.KeywordSearchQuery
import sources.heise.queries.KeywordSearchQueryConfig

fun main(args: Array<String>) {
    // Scheduler-Modus
    if (args.isNotEmpty() && args[0] == "--scheduler") {
        startScheduler()
        return
    }

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
    ║  5. 🕐 Scheduler starten              ║
    ║  6. 📋 Scheduler-Config anzeigen      ║
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
        "5", "scheduler" -> startScheduler()
        "6", "config" -> showSchedulerConfig()
        else -> println("❌ Unbekannte Auswahl: $input")
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Scheduler
// ══════════════════════════════════════════════════════════════════════════════

fun startScheduler() {
    println("\n🚀 Starte FeedKrake Scheduler...")
    println("   (Ctrl+C zum Beenden)\n")

    val config = SchedulerConfig.load()
    val scheduler = Scheduler(config)

    // Shutdown Hook für sauberes Beenden
    Runtime.getRuntime().addShutdownHook(Thread {
        scheduler.stop()
    })

    scheduler.start()
}

fun showSchedulerConfig() {
    println("\n📋 Scheduler-Konfiguration:")
    println("─".repeat(50))

    val config = SchedulerConfig.load()

    println("Check-Interval: ${config.checkIntervalSeconds} Sekunden")
    println("Timezone: ${config.timezone}")
    println("\nAktive Tasks (${config.tasks.count { it.enabled }}):")

    config.tasks.filter { it.enabled }.forEach { task ->
        println("  • ${task.name} (${task.type})")
    }

    println("\n📝 Konfiguration bearbeiten:")
    println("   src/scheduler/config/scheduler_config.txt")
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
