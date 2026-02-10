package sources.pubg.config

import java.io.File

object PubgConfigLoader {

    private const val CONFIG_DIR = "src/sources/pubg/config"
    private const val API_KEY_FILE = "pubg_api_key.txt"

    fun loadApiKey(): String? {
        val file = File("$CONFIG_DIR/$API_KEY_FILE")

        if (!file.exists()) {
            println("❌ API Key Datei nicht gefunden!")
            println("   Bitte erstelle: $CONFIG_DIR/$API_KEY_FILE")
            println("   Inhalt: Dein PUBG API Key (https://developer.pubg.com/)")
            return null
        }

        val apiKey = file.readText().trim()

        if (apiKey.isEmpty()) {
            println("❌ API Key Datei ist leer!")
            println("   Bitte trage deinen API Key in $CONFIG_DIR/$API_KEY_FILE ein")
            return null
        }

        println("✅ API Key geladen aus $CONFIG_DIR/$API_KEY_FILE")
        return apiKey
    }
}
