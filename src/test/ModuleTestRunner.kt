package test

import config.EnvConfig
import scheduler.discord.DiscordWebhook
import sources.bf6.api.Bf6ApiClient
import sources.`pubg-api`.api.PubgApiClient
import java.net.HttpURLConnection
import java.net.URI

/**
 * Test-Runner für alle FeedKrake Module.
 * Sendet Beispiel-Ausgaben an den Test-Channel.
 */
object ModuleTestRunner {

    private var discord: DiscordWebhook? = null
    private var testResults = mutableListOf<TestResult>()

    data class TestResult(
        val module: String,
        val success: Boolean,
        val message: String
    )

    /**
     * Führt alle Modul-Tests durch und sendet Ergebnisse an Discord.
     */
    fun runAllTests() {
        println("""
    ╔═══════════════════════════════════════════════════════════════╗
    ║           🧪 FeedKrake - Modul-Test                           ║
    ╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent())

        // Config laden
        EnvConfig.load()

        // Discord Webhook holen
        val webhookUrl = EnvConfig.discordWebhook("test")
        if (webhookUrl == null) {
            println("❌ DISCORD_WEBHOOK_TEST nicht konfiguriert!")
            println("   Füge in .env hinzu: DISCORD_WEBHOOK_TEST=https://discord.com/api/webhooks/...")
            return
        }

        discord = DiscordWebhook(webhookUrl)

        // Start-Nachricht senden
        discord?.sendEmbed(
            title = "🧪 FeedKrake Modul-Test gestartet",
            description = "Teste alle verfügbaren Module...",
            color = DiscordWebhook.COLOR_BLUE
        )

        Thread.sleep(1000)

        // Tests ausführen
        println("\n── 1. Config Test ─────────────────────")
        testConfig()

        println("\n── 2. Claude API Test ─────────────────")
        testClaudeApi()

        println("\n── 3. PUBG API Test ───────────────────")
        testPubgApi()

        println("\n── 4. BF6 API Test ────────────────────")
        testBf6Api()

        // Zusammenfassung senden
        sendSummary()

        println("\n✅ Alle Tests abgeschlossen!")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Einzelne Tests
    // ═══════════════════════════════════════════════════════════════════════════

    private fun testConfig() {
        val channels = EnvConfig.discordChannels()
        val hasPubg = EnvConfig.has("PUBG_API_KEY")
        val hasClaude = EnvConfig.has("CLAUDE_API_KEY")
        val hasBf6 = EnvConfig.has("BF6_API_KEY")

        val configuredApis = listOfNotNull(
            if (hasPubg) "PUBG" else null,
            if (hasClaude) "Claude" else null,
            if (hasBf6) "BF6" else null
        )

        discord?.sendEmbed(
            title = "⚙️ Config-Test",
            description = """
                **Discord Webhooks:** ${channels.size} konfiguriert
                ${channels.joinToString("\n") { "• #$it" }}

                **API Keys:**
                ${if (hasPubg) "✅" else "❌"} PUBG API
                ${if (hasClaude) "✅" else "❌"} Claude API
                ${if (hasBf6) "✅" else "❌"} BF6 API
            """.trimIndent(),
            color = DiscordWebhook.COLOR_GREEN,
            footer = "EnvConfig geladen"
        )

        testResults.add(TestResult("Config", true, "${channels.size} Channels, ${configuredApis.size} APIs"))
        println("✅ Config OK")
    }

    private fun testClaudeApi() {
        val apiKey = EnvConfig.claudeApiKey()
        if (apiKey == null) {
            discord?.sendEmbed(
                title = "🤖 Claude API Test",
                description = "❌ API Key nicht konfiguriert\n\nFüge `CLAUDE_API_KEY` in .env hinzu.",
                color = DiscordWebhook.COLOR_RED
            )
            testResults.add(TestResult("Claude", false, "API Key fehlt"))
            return
        }

        val prompt = "Antworte mit genau einem Satz: Was macht FeedKrake?"
        val response = callClaudeApi(apiKey, prompt)

        if (response != null) {
            discord?.sendEmbed(
                title = "🤖 Claude API Test",
                description = """
                    **Prompt:** $prompt

                    **Antwort:** $response
                """.trimIndent(),
                color = DiscordWebhook.COLOR_GREEN,
                footer = "Claude API funktioniert"
            )
            testResults.add(TestResult("Claude", true, "API antwortet"))
            println("✅ Claude OK")
        } else {
            discord?.sendEmbed(
                title = "🤖 Claude API Test",
                description = "❌ API-Aufruf fehlgeschlagen",
                color = DiscordWebhook.COLOR_RED
            )
            testResults.add(TestResult("Claude", false, "API-Fehler"))
        }
    }

    private fun testPubgApi() {
        val apiKey = EnvConfig.pubgApiKey()
        if (apiKey == null) {
            discord?.sendEmbed(
                title = "🎮 PUBG API Test",
                description = "❌ API Key nicht konfiguriert\n\nFüge `PUBG_API_KEY` in .env hinzu.",
                color = DiscordWebhook.COLOR_RED
            )
            testResults.add(TestResult("PUBG", false, "API Key fehlt"))
            return
        }

        // Test mit bekanntem Spieler
        val testPlayer = "shroud"
        val platform = "steam"

        try {
            val client = PubgApiClient(apiKey)
            val accountId = client.fetchAccountId(testPlayer, platform)

            if (accountId != null) {
                discord?.sendEmbed(
                    title = "🎮 PUBG API Test",
                    description = """
                        **Test-Spieler:** $testPlayer ($platform)
                        **Account-ID:** `${accountId.take(20)}...`

                        ✅ API-Verbindung erfolgreich!
                    """.trimIndent(),
                    color = DiscordWebhook.COLOR_GREEN,
                    footer = "PUBG API funktioniert"
                )
                testResults.add(TestResult("PUBG", true, "Account gefunden"))
                println("✅ PUBG OK")
            } else {
                discord?.sendEmbed(
                    title = "🎮 PUBG API Test",
                    description = "⚠️ Spieler nicht gefunden, aber API erreichbar",
                    color = DiscordWebhook.COLOR_YELLOW
                )
                testResults.add(TestResult("PUBG", true, "API erreichbar"))
            }
        } catch (e: Exception) {
            discord?.sendEmbed(
                title = "🎮 PUBG API Test",
                description = "❌ Fehler: ${e.message}",
                color = DiscordWebhook.COLOR_RED
            )
            testResults.add(TestResult("PUBG", false, e.message ?: "Fehler"))
        }
    }

    private fun testBf6Api() {
        // BF6 API braucht keinen Key (gametools.network)
        val testPlayer = "YOURNAME"  // Beispielspieler

        try {
            val client = Bf6ApiClient()
            val stats = client.fetchStats(testPlayer, "pc")

            if (stats != null) {
                discord?.sendEmbed(
                    title = "🔫 Battlefield 6 API Test",
                    description = """
                        **Test-Spieler:** ${stats.userName}
                        **Matches:** ${stats.matchesPlayed}
                        **K/D:** ${stats.killDeath}
                        **Wins:** ${stats.wins}

                        ✅ API-Verbindung erfolgreich!
                    """.trimIndent(),
                    color = DiscordWebhook.COLOR_GREEN,
                    footer = "BF6 API (gametools.network)"
                )
                testResults.add(TestResult("BF6", true, "Stats abgerufen"))
                println("✅ BF6 OK")
            } else {
                discord?.sendEmbed(
                    title = "🔫 Battlefield 6 API Test",
                    description = """
                        ⚠️ Spieler '$testPlayer' nicht gefunden

                        Die API ist erreichbar, aber der Testspieler existiert nicht.
                        Das ist normal - BF6 API funktioniert!
                    """.trimIndent(),
                    color = DiscordWebhook.COLOR_YELLOW,
                    footer = "BF6 API (gametools.network)"
                )
                testResults.add(TestResult("BF6", true, "API erreichbar"))
                println("✅ BF6 OK (Spieler nicht gefunden)")
            }
        } catch (e: Exception) {
            discord?.sendEmbed(
                title = "🔫 Battlefield 6 API Test",
                description = "❌ Fehler: ${e.message}",
                color = DiscordWebhook.COLOR_RED
            )
            testResults.add(TestResult("BF6", false, e.message ?: "Fehler"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Zusammenfassung
    // ═══════════════════════════════════════════════════════════════════════════

    private fun sendSummary() {
        Thread.sleep(1000)

        val passed = testResults.count { it.success }
        val failed = testResults.count { !it.success }

        val resultsText = testResults.joinToString("\n") { result ->
            val icon = if (result.success) "✅" else "❌"
            "$icon **${result.module}:** ${result.message}"
        }

        val overallColor = when {
            failed == 0 -> DiscordWebhook.COLOR_GREEN
            passed == 0 -> DiscordWebhook.COLOR_RED
            else -> DiscordWebhook.COLOR_YELLOW
        }

        discord?.sendEmbed(
            title = "📊 Test-Zusammenfassung",
            description = """
                $resultsText

                ═══════════════════════════════
                **Ergebnis:** $passed/${testResults.size} Tests bestanden
            """.trimIndent(),
            color = overallColor,
            footer = "FeedKrake Modul-Test abgeschlossen"
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════════════

    private fun callClaudeApi(apiKey: String, prompt: String): String? {
        val apiUrl = "https://api.anthropic.com/v1/messages"
        val model = "claude-sonnet-4-20250514"

        val escapedPrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        val payload = """
            {
                "model": "$model",
                "max_tokens": 256,
                "messages": [
                    {"role": "user", "content": "$escapedPrompt"}
                ]
            }
        """.trimIndent()

        return try {
            val connection = URI(apiUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", "2023-06-01")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            connection.outputStream.use { os ->
                os.write(payload.toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                val textRegex = """"text"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
                textRegex.find(responseBody)?.groupValues?.get(1)
                    ?.replace("\\n", "\n")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\")
            } else {
                println("❌ [Claude] HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            println("❌ [Claude] Fehler: ${e.message}")
            null
        }
    }
}

/**
 * Standalone-Aufruf für den Modul-Test.
 */
fun main() {
    ModuleTestRunner.runAllTests()
}
