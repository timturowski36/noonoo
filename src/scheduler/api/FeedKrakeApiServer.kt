package scheduler.api

import com.sun.net.httpserver.HttpServer
import config.EnvConfig
import scheduler.FeedKrakeScheduler
import scheduler.config.FeedKrakeConfig
import scheduler.discord.DiscordWebhook
import test.ModuleTestRunner
import java.net.InetSocketAddress
import java.net.URI
import java.net.HttpURLConnection

/**
 * Einfacher HTTP-Server für Remote-Trigger.
 * Ermöglicht das Auslösen von Jobs über HTTP-Requests.
 */
class FeedKrakeApiServer(
    private val port: Int = 8080,
    private val scheduler: FeedKrakeScheduler? = null
) {
    private var server: HttpServer? = null
    private val config = FeedKrakeConfig.load()

    fun start() {
        server = HttpServer.create(InetSocketAddress(port), 0)

        // GET / - Übersicht
        server?.createContext("/") { exchange ->
            val response = buildOverviewHtml()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        // GET /status - JSON Status
        server?.createContext("/status") { exchange ->
            val response = """{"status": "running", "jobs": ${config.jobs.size}, "channels": ${config.channels.size}}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        // GET /trigger/{jobName} - Job auslösen
        server?.createContext("/trigger") { exchange ->
            val path = exchange.requestURI.path
            val jobName = path.removePrefix("/trigger/").trim()

            val (status, response) = if (jobName.isBlank()) {
                400 to """{"error": "Job-Name fehlt. Nutze /trigger/JobName"}"""
            } else {
                val job = config.jobs.find { it.name.equals(jobName, ignoreCase = true) }
                if (job != null) {
                    println("🌐 [API] Trigger: ${job.name}")
                    // Job ausführen (simplified - direkt hier)
                    triggerJob(job.name)
                    200 to """{"triggered": "${job.name}", "channel": "${job.channel}"}"""
                } else {
                    404 to """{"error": "Job nicht gefunden: $jobName"}"""
                }
            }

            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        // GET /test/all - Alle Module testen
        server?.createContext("/test/all") { exchange ->
            Thread {
                ModuleTestRunner.runAllTests()
            }.start()

            val response = """{"started": true, "message": "Modul-Tests gestartet, Ergebnisse werden an #test gesendet"}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        // GET /test/claude - Claude API Test mit Discord-Output
        server?.createContext("/test/claude") { exchange ->
            val channel = exchange.requestURI.query
                ?.split("&")
                ?.find { it.startsWith("channel=") }
                ?.removePrefix("channel=")
                ?: "test"

            val (status, response) = runClaudeTest(channel)

            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        // GET /jobs - Liste aller Jobs
        server?.createContext("/jobs") { exchange ->
            val jobsJson = config.jobs.joinToString(",\n    ") { job ->
                """{"name": "${job.name}", "module": "${job.module}", "channel": "${job.channel}"}"""
            }
            val response = """{"jobs": [
    $jobsJson
]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        server?.executor = null
        server?.start()

        println("""
    ╔═══════════════════════════════════════════════════════════════╗
    ║           🌐 FeedKrake API Server gestartet                   ║
    ╠═══════════════════════════════════════════════════════════════╣
    ║  Port: $port                                                  ║
    ║                                                               ║
    ║  Endpoints:                                                   ║
    ║    http://localhost:$port/              - Übersicht (Browser) ║
    ║    http://localhost:$port/status        - Status (JSON)       ║
    ║    http://localhost:$port/jobs          - Alle Jobs (JSON)    ║
    ║    http://localhost:$port/trigger/X     - Job X auslösen      ║
    ║    http://localhost:$port/test/all      - Alle Module testen  ║
    ║    http://localhost:$port/test/claude   - Claude API Test     ║
    ╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent())
    }

    fun stop() {
        server?.stop(0)
        println("🛑 [API] Server gestoppt")
    }

    private fun triggerJob(jobName: String) {
        val job = config.jobs.find { it.name.equals(jobName, ignoreCase = true) } ?: return
        val webhookUrl = config.channels[job.channel] ?: return

        // Einfache Trigger-Nachricht
        Thread {
            try {
                val webhook = DiscordWebhook(webhookUrl)
                webhook.sendEmbed(
                    title = "🔔 ${job.name} (manuell)",
                    description = "Job wurde manuell über API getriggert.\nModul: ${job.module}",
                    color = 0x5865F2
                )
            } catch (e: Exception) {
                println("❌ [API] Fehler beim Trigger: ${e.message}")
            }
        }.start()
    }

    /**
     * Führt einen Claude API Test durch und postet das Ergebnis in Discord.
     */
    private fun runClaudeTest(channel: String): Pair<Int, String> {
        println("🧪 [API] Claude-Test für Channel: $channel")

        // Config laden
        EnvConfig.load()

        // Discord Webhook holen
        val webhookUrl = EnvConfig.discordWebhook(channel)
        if (webhookUrl == null) {
            return 400 to """{"error": "Kein Webhook für Channel '$channel' konfiguriert"}"""
        }

        // Claude API Key prüfen
        val apiKey = EnvConfig.claudeApiKey()
        if (apiKey == null) {
            return 400 to """{"error": "Claude API Key nicht konfiguriert"}"""
        }

        // Claude API aufrufen
        val prompt = "Schreibe einen kurzen, witzigen Fakt über Tintenfische (max 2 Sätze, auf Deutsch)."
        val claudeResponse = callClaudeApi(apiKey, prompt)

        if (claudeResponse == null) {
            return 500 to """{"error": "Claude API Aufruf fehlgeschlagen"}"""
        }

        // An Discord senden
        val discord = DiscordWebhook(webhookUrl)
        val success = discord.sendEmbed(
            title = "🐙 FeedKrake × Claude API Test",
            description = """
                **Anfrage:**
                $prompt

                **Antwort:**
                $claudeResponse
            """.trimIndent(),
            color = DiscordWebhook.COLOR_BLUE,
            footer = "Test via API Server"
        )

        return if (success) {
            200 to """{"success": true, "channel": "$channel", "response": "${claudeResponse.replace("\"", "\\\"").replace("\n", "\\n")}"}"""
        } else {
            500 to """{"error": "Discord-Nachricht konnte nicht gesendet werden"}"""
        }
    }

    /**
     * Ruft die Claude API auf und gibt die Antwort zurück.
     */
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
                // Extract text from response
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

    private fun buildOverviewHtml(): String {
        val jobRows = config.jobs.joinToString("\n") { job ->
            """
            <tr>
                <td>${job.name}</td>
                <td><code>${job.module}</code></td>
                <td>#${job.channel}</td>
                <td>${job.schedule}</td>
                <td><button onclick="trigger('${job.name}')">▶️ Trigger</button></td>
            </tr>
            """.trimIndent()
        }

        return """
<!DOCTYPE html>
<html>
<head>
    <title>🐙 FeedKrake</title>
    <meta charset="utf-8">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
               background: #1a1a2e; color: #eee; padding: 20px; }
        h1 { color: #5865F2; }
        table { border-collapse: collapse; width: 100%; margin: 20px 0; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #333; }
        th { background: #16213e; }
        tr:hover { background: #16213e; }
        button { background: #5865F2; color: white; border: none; padding: 8px 16px;
                 border-radius: 4px; cursor: pointer; }
        button:hover { background: #4752C4; }
        code { background: #333; padding: 2px 6px; border-radius: 3px; }
        .status { color: #57F287; }
        #result { margin-top: 10px; padding: 10px; background: #16213e; border-radius: 4px; }
    </style>
</head>
<body>
    <h1>🐙 FeedKrake Dashboard</h1>
    <p class="status">● Server läuft</p>

    <h2>📋 Jobs</h2>
    <table>
        <tr><th>Name</th><th>Modul</th><th>Channel</th><th>Zeitplan</th><th>Aktion</th></tr>
        $jobRows
    </table>

    <div id="result"></div>

    <h2>🧪 Tests</h2>
    <p>
        <button onclick="testAll()">🧪 Alle Module testen</button>
        <button onclick="testClaude()">🤖 Claude API Test</button>
        <span id="test-result"></span>
    </p>

    <h2>🔗 API Endpoints</h2>
    <ul>
        <li><a href="/status">/status</a> - Server-Status (JSON)</li>
        <li><a href="/jobs">/jobs</a> - Alle Jobs (JSON)</li>
        <li><code>/trigger/{JobName}</code> - Job manuell auslösen</li>
        <li><a href="/test/all">/test/all</a> - Alle Module testen</li>
        <li><a href="/test/claude?channel=test">/test/claude</a> - Claude API Test</li>
    </ul>

    <script>
        function trigger(jobName) {
            fetch('/trigger/' + encodeURIComponent(jobName))
                .then(r => r.json())
                .then(data => {
                    document.getElementById('result').innerHTML =
                        '<strong>✅ Triggered:</strong> ' + JSON.stringify(data);
                })
                .catch(e => {
                    document.getElementById('result').innerHTML =
                        '<strong>❌ Fehler:</strong> ' + e;
                });
        }

        function testAll() {
            document.getElementById('test-result').innerHTML = '⏳ Tests gestartet...';
            fetch('/test/all')
                .then(r => r.json())
                .then(data => {
                    document.getElementById('test-result').innerHTML =
                        '✅ Tests laufen - siehe #test Channel';
                })
                .catch(e => {
                    document.getElementById('test-result').innerHTML =
                        '❌ ' + e;
                });
        }

        function testClaude() {
            document.getElementById('test-result').innerHTML = '⏳ Claude-Test läuft...';
            fetch('/test/claude?channel=test')
                .then(r => r.json())
                .then(data => {
                    if (data.success) {
                        document.getElementById('test-result').innerHTML =
                            '✅ Gesendet an #test';
                    } else {
                        document.getElementById('test-result').innerHTML =
                            '❌ ' + data.error;
                    }
                })
                .catch(e => {
                    document.getElementById('test-result').innerHTML =
                        '❌ ' + e;
                });
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
