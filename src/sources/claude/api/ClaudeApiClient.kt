package sources.claude.api

import sources.claude.model.ClaudeResponse
import sources.claude.prompts.PromptContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ClaudeApiClient(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-20250514",
    private val maxTokens: Int = 4096
) {
    private val httpClient = HttpClient.newHttpClient()
    private val baseUrl = "https://api.anthropic.com/v1/messages"

    // ─────────────────────────────────────────────────────────────────────────
    // Einfache Nachricht senden (ohne Kontext)
    // ─────────────────────────────────────────────────────────────────────────

    fun sendMessage(userMessage: String): ClaudeResponse? {
        return sendWithContext(null, userMessage)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nachricht mit vorgefertigtem Kontext senden
    // ─────────────────────────────────────────────────────────────────────────

    fun sendWithContext(context: PromptContext?, userMessage: String): ClaudeResponse? {
        return try {
            val systemPrompt = context?.systemPrompt
            val fullUserMessage = if (context != null) {
                "${context.userPrefix}$userMessage${context.userSuffix}"
            } else {
                userMessage
            }

            val requestBody = buildRequestBody(systemPrompt, fullUserMessage)
            println("🌐 [Claude] Sende Anfrage...")

            val request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                val errorBody = response.body()
                println("❌ [Claude] HTTP ${response.statusCode()}")

                // Bekannte Fehler mit hilfreichen Meldungen
                when {
                    errorBody.contains("credit balance is too low") -> {
                        println("   💳 Dein API-Guthaben ist aufgebraucht!")
                        println("   → Gehe zu: https://console.anthropic.com/settings/billing")
                    }
                    errorBody.contains("invalid_api_key") || errorBody.contains("authentication") -> {
                        println("   🔑 API-Key ungültig oder abgelaufen!")
                        println("   → Prüfe: src/sources/claude/config/claude_api_key.txt")
                    }
                    errorBody.contains("rate_limit") -> {
                        println("   ⏱️ Rate-Limit erreicht, bitte warten...")
                    }
                    else -> {
                        println("   ${errorBody.take(200)}")
                    }
                }
                return null
            }

            parseResponse(response.body())

        } catch (e: Exception) {
            println("❌ [Claude] Fehler: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strukturierte Abfrage für DTO-Parsing (JSON-Ausgabe erzwingen)
    // ─────────────────────────────────────────────────────────────────────────

    fun queryForDto(context: PromptContext, userMessage: String): ClaudeResponse? {
        val jsonContext = context.copy(
            systemPrompt = (context.systemPrompt ?: "") + """

WICHTIG: Antworte NUR mit validem JSON. Keine Erklärungen, kein Markdown, nur das JSON-Objekt.
""",
            userSuffix = context.userSuffix + "\n\nAntworte ausschließlich mit JSON."
        )
        return sendWithContext(jsonContext, userMessage)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Webseite laden und mit Claude analysieren
    // ─────────────────────────────────────────────────────────────────────────

    private val webFetcher = WebFetcher()

    /**
     * Lädt eine Webseite und analysiert sie mit Claude.
     * @param url Die URL der Webseite
     * @param prompt Was soll aus der Seite extrahiert werden?
     * @param context Optionaler Prompt-Kontext für strukturierte Ausgabe
     */
    fun analyzeWebpage(url: String, prompt: String, context: PromptContext? = null): ClaudeResponse? {
        val webContent = webFetcher.fetch(url) ?: return null

        val fullPrompt = buildString {
            appendLine("URL: $url")
            webContent.title?.let { appendLine("Titel: $it") }
            appendLine()
            appendLine("=== WEBSEITEN-INHALT ===")
            appendLine(webContent.truncatedText(12000))
            appendLine("=== ENDE WEBSEITEN-INHALT ===")
            appendLine()
            append(prompt)
        }

        return if (context != null) {
            sendWithContext(context, fullPrompt)
        } else {
            sendMessage(fullPrompt)
        }
    }

    /**
     * Lädt eine Webseite und extrahiert strukturierte Daten als JSON.
     * @param url Die URL der Webseite
     * @param context Der Prompt-Kontext (definiert das erwartete JSON-Schema)
     * @param additionalPrompt Zusätzliche Anweisungen
     */
    fun extractFromWebpage(url: String, context: PromptContext, additionalPrompt: String = ""): ClaudeResponse? {
        val webContent = webFetcher.fetch(url) ?: return null

        val fullPrompt = buildString {
            appendLine("Analysiere folgende Webseite und extrahiere die Daten:")
            appendLine()
            appendLine("URL: $url")
            webContent.title?.let { appendLine("Titel: $it") }
            appendLine()
            appendLine("=== WEBSEITEN-INHALT ===")
            appendLine(webContent.truncatedText(12000))
            appendLine("=== ENDE WEBSEITEN-INHALT ===")
            if (additionalPrompt.isNotEmpty()) {
                appendLine()
                append(additionalPrompt)
            }
        }

        return queryForDto(context, fullPrompt)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request Body bauen (manuell, da kein JSON-Library)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildRequestBody(systemPrompt: String?, userMessage: String): String {
        val escapedUser = escapeJson(userMessage)
        val messagesJson = """[{"role":"user","content":"$escapedUser"}]"""

        return if (systemPrompt != null) {
            val escapedSystem = escapeJson(systemPrompt)
            """{"model":"$model","max_tokens":$maxTokens,"system":"$escapedSystem","messages":$messagesJson}"""
        } else {
            """{"model":"$model","max_tokens":$maxTokens,"messages":$messagesJson}"""
        }
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manuelles JSON String-Parsing (vermeidet Regex StackOverflow)
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractJsonStringValue(json: String, key: String): String? {
        val keyPattern = "\"$key\""
        var pos = json.indexOf(keyPattern)
        if (pos == -1) return null

        // Finde ':' nach dem Key
        pos = json.indexOf(':', pos + keyPattern.length)
        if (pos == -1) return null

        // Überspringe Whitespace
        pos++
        while (pos < json.length && json[pos].isWhitespace()) pos++

        // Erwarte '"'
        if (pos >= json.length || json[pos] != '"') return null
        pos++

        // Extrahiere String-Inhalt mit Escape-Handling
        val result = StringBuilder()
        while (pos < json.length) {
            when (val c = json[pos]) {
                '\\' -> {
                    pos++
                    if (pos >= json.length) break
                    when (json[pos]) {
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        '"' -> result.append('"')
                        '\\' -> result.append('\\')
                        else -> result.append(json[pos])
                    }
                }
                '"' -> return result.toString()
                else -> result.append(c)
            }
            pos++
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response parsen
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseResponse(body: String): ClaudeResponse? {
        // Extrahiere "text" aus content[0].text (manuelles Parsing um StackOverflow zu vermeiden)
        val text = extractJsonStringValue(body, "text") ?: return null

        // Extrahiere usage Informationen
        val inputTokensRegex = Regex(""""input_tokens"\s*:\s*(\d+)""")
        val outputTokensRegex = Regex(""""output_tokens"\s*:\s*(\d+)""")
        val inputTokens = inputTokensRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val outputTokens = outputTokensRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        // Extrahiere Model
        val modelRegex = Regex(""""model"\s*:\s*"([^"]+)"""")
        val usedModel = modelRegex.find(body)?.groupValues?.get(1) ?: model

        println("✅ [Claude] Antwort erhalten (${inputTokens}+${outputTokens} Tokens)")

        return ClaudeResponse(
            text = text,
            model = usedModel,
            inputTokens = inputTokens,
            outputTokens = outputTokens
        )
    }
}
