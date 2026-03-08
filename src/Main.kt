import sources.heise.HeiseSource

fun main() {
    println("═══════════════════════════════════════")
    println("   📰 Heise RSS Module - Demo")
    println("═══════════════════════════════════════")

    // ─── Standard News ───────────────────────────────────────────────────────
    println("\n── 📰 Heise Online (Top 5) ─────────────")
    HeiseSource.news(5).fetchArticles().fold(
        onSuccess = { articles ->
            articles.forEach { println(it.discordFormat()) }
        },
        onFailure = { println("❌ Fehler: ${it.message}") }
    )

    // ─── KI/AI News ──────────────────────────────────────────────────────────
    println("\n── 🤖 KI News ──────────────────────────")
    HeiseSource.ki(5).fold(
        onSuccess = { articles ->
            if (articles.isEmpty()) {
                println("Keine KI-Artikel gefunden")
            } else {
                articles.forEach { println(it.discordFormat()) }
            }
        },
        onFailure = { println("❌ Fehler: ${it.message}") }
    )

    // ─── Security News ───────────────────────────────────────────────────────
    println("\n── 🔒 Security News ────────────────────")
    HeiseSource.security(5).fetchArticles().fold(
        onSuccess = { articles ->
            articles.forEach { println(it.discordFormat()) }
        },
        onFailure = { println("❌ Fehler: ${it.message}") }
    )

    // ─── Gaming News ─────────────────────────────────────────────────────────
    println("\n── 🎮 Gaming News ──────────────────────")
    HeiseSource.gaming(5).fold(
        onSuccess = { articles ->
            if (articles.isEmpty()) {
                println("Keine Gaming-Artikel gefunden")
            } else {
                articles.forEach { println(it.discordFormat()) }
            }
        },
        onFailure = { println("❌ Fehler: ${it.message}") }
    )

    // ─── Developer News ──────────────────────────────────────────────────────
    println("\n── 💻 Developer News ───────────────────")
    HeiseSource.developer(5).fetchArticles().fold(
        onSuccess = { articles ->
            articles.forEach { println(it.discordFormat()) }
        },
        onFailure = { println("❌ Fehler: ${it.message}") }
    )

    println("\n═══════════════════════════════════════")
    println("   ✅ Demo abgeschlossen")
    println("═══════════════════════════════════════")
}
