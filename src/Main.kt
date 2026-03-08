import config.EnvConfig
import outputs.discord.DiscordBot
import sources.bf6.model.Bf6Stats
import sources.pubg.model.PlayerStats
import sources.bundesliga.model.TabellenEintrag
import sources.heise.model.HeiseArticle
import java.time.Instant

fun main() {
    println("FeedKrake - Module Demo")
    EnvConfig.load()

    val discord = DiscordBot.create()

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBG Demo
    // ═══════════════════════════════════════════════════════════════════════════
    val pubgStats = PlayerStats(
        wins = 3,
        matches = 47,
        kills = 89,
        deaths = 44,
        damageDealt = 12500.0,
        assists = 31,
        longestKill = 312.5,
        headshotKills = 18,
        topTens = 21,
        revives = 15,
        knockdowns = 67
    )

    val pubgMessage = buildString {
        appendLine("```")
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║              🎮 PUBG Weekly Stats                             ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine(pubgStats.basicFormat("📊 PhilipNC - Diese Woche:"))
        appendLine()
        appendLine(pubgStats.weeklyExtras())
        appendLine("```")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BF6 Demo
    // ═══════════════════════════════════════════════════════════════════════════
    val bf6Stats = Bf6Stats(
        userName = "PhilipNC",
        kills = 4521,
        deaths = 2890,
        killDeath = 1.56,
        wins = 312,
        losses = 245,
        matchesPlayed = 557,
        winPercent = "56%",
        killAssists = 1832,
        headShots = 1204,
        headshotPercent = "27%",
        revives = 892,
        accuracy = "21.3%",
        timePlayed = "142h 35m",
        killsPerMatch = 8.1
    )

    val bf6Message = buildString {
        appendLine("```")
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║              🪖 Battlefield 6 Stats                           ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("Spieler: ${bf6Stats.userName}")
        appendLine()
        appendLine(bf6Stats.discordFormat())
        appendLine("```")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bundesliga Demo
    // ═══════════════════════════════════════════════════════════════════════════
    val tabelle = listOf(
        TabellenEintrag("bl1", 1, 40, "Bayern München", "FCB", 58, 22, 68, 24, 18, 2, 4, 46),
        TabellenEintrag("bl1", 2, 7, "Bayer Leverkusen", "B04", 55, 28, 62, 24, 17, 3, 4, 34),
        TabellenEintrag("bl1", 3, 131, "VfB Stuttgart", "VFB", 51, 32, 59, 24, 15, 5, 4, 27),
        TabellenEintrag("bl1", 4, 16, "Borussia Dortmund", "BVB", 47, 35, 55, 24, 14, 5, 5, 20),
        TabellenEintrag("bl1", 5, 112, "RB Leipzig", "RBL", 45, 31, 48, 24, 13, 6, 5, 17)
    )

    val bundesligaMessage = buildString {
        appendLine("```")
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║              ⚽ Bundesliga Tabelle (Top 5)                    ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("Pl  Team                   Sp   S  U  N   Tore    Diff   Pkt")
        appendLine("─────────────────────────────────────────────────────────────")
        tabelle.forEach { t ->
            val team = t.teamName.padEnd(20).take(20)
            val tore = "${t.goals}:${t.opponentGoals}".padStart(6)
            val diff = (if (t.goalDiff >= 0) "+${t.goalDiff}" else "${t.goalDiff}").padStart(5)
            appendLine("${t.platz.toString().padStart(2)}  $team ${t.matches.toString().padStart(2)}  ${t.won.toString().padStart(2)} ${t.draw.toString().padStart(2)} ${t.lost.toString().padStart(2)}  $tore  $diff   ${t.points.toString().padStart(2)}")
        }
        appendLine("```")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Heise Demo
    // ═══════════════════════════════════════════════════════════════════════════
    val heiseArticles = listOf(
        HeiseArticle(
            title = "Linux 6.8 bringt Performance-Boost für AMD-Grafikkarten",
            link = "https://heise.de/news/linux-68",
            description = "Der neue Kernel verbessert die Unterstützung für RDNA3-GPUs erheblich...",
            pubDate = Instant.now().minusSeconds(3600),
            guid = "1"
        ),
        HeiseArticle(
            title = "Kotlin 2.0: Das ändert sich für Entwickler",
            link = "https://heise.de/news/kotlin-20",
            description = "JetBrains hat die finale Version von Kotlin 2.0 veröffentlicht...",
            pubDate = Instant.now().minusSeconds(7200),
            guid = "2"
        ),
        HeiseArticle(
            title = "Steam Deck 2: Valve bestätigt Entwicklung",
            link = "https://heise.de/news/steam-deck-2",
            description = "Gabe Newell spricht erstmals über die nächste Generation...",
            pubDate = Instant.now().minusSeconds(10800),
            guid = "3"
        )
    )

    val heiseMessage = buildString {
        appendLine("```")
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║              📰 Heise News (Letzte 3)                         ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        heiseArticles.forEach { article ->
            appendLine("• ${article.discordFormat()}")
        }
        appendLine("```")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // An Test-Channel senden
    // ═══════════════════════════════════════════════════════════════════════════
    println("\n📤 Sende Demo-Output an Test-Channel...")

    val fullMessage = buildString {
        appendLine("🐙 **FeedKrake Module Demo**")
        appendLine()
        append(pubgMessage)
        appendLine()
        append(bf6Message)
        appendLine()
        append(bundesligaMessage)
        appendLine()
        append(heiseMessage)
    }

    val success = discord.sendMessageToChannel("test", fullMessage)

    if (success) {
        println("✅ Demo erfolgreich gesendet!")
    } else {
        println("❌ Fehler beim Senden")
    }
}
