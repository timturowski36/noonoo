package sources.claude.model

data class ClaudeResponse(
    val text: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int
) {
    val totalTokens: Int get() = inputTokens + outputTokens

    // ─────────────────────────────────────────────────────────────────────────
    // JSON-Block aus der Antwort extrahieren (für DTO-Parsing)
    // ─────────────────────────────────────────────────────────────────────────

    fun extractJsonBlock(): String? {
        // Versuche JSON-Block zwischen ```json ... ``` zu finden
        val codeBlockRegex = Regex("""```json\s*([\s\S]*?)\s*```""")
        codeBlockRegex.find(text)?.groupValues?.get(1)?.let { return it.trim() }

        // Versuche JSON-Block zwischen ``` ... ``` zu finden
        val genericBlockRegex = Regex("""```\s*([\s\S]*?)\s*```""")
        genericBlockRegex.find(text)?.groupValues?.get(1)?.let {
            if (it.trim().startsWith("{") || it.trim().startsWith("[")) {
                return it.trim()
            }
        }

        // Falls die Antwort direkt JSON ist
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }

        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Einfache Key-Value Extraktion aus JSON
    // ─────────────────────────────────────────────────────────────────────────

    fun extractString(key: String): String? {
        val json = extractJsonBlock() ?: text
        val regex = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        return regex.find(json)?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
    }

    fun extractInt(key: String): Int? {
        val json = extractJsonBlock() ?: text
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun extractDouble(key: String): Double? {
        val json = extractJsonBlock() ?: text
        val regex = Regex(""""$key"\s*:\s*([\d.]+)""")
        return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Array von Objekten extrahieren (für Listen wie Bundesligatabelle)
    // ─────────────────────────────────────────────────────────────────────────

    fun extractArray(key: String): List<String>? {
        val json = extractJsonBlock() ?: text
        val arrayRegex = Regex(""""$key"\s*:\s*\[([\s\S]*?)]""")
        val arrayContent = arrayRegex.find(json)?.groupValues?.get(1) ?: return null

        // Extrahiere einzelne Objekte aus dem Array
        val objectRegex = Regex("""\{[^{}]*}""")
        return objectRegex.findAll(arrayContent).map { it.value }.toList()
    }
}
