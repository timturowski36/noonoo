import domain.model.QueryResult
import outputs.discord.renderers.BundesligaDiscordRenderer
import sources.bundesliga.BundesligaModule
import sources.bundesliga.model.BundesligaSettings
import sources.bundesliga.queries.NaechsteSpieleQuery
import sources.bundesliga.queries.NaechsteSpieleQuerySettings
import sources.bundesliga.queries.TabelleQuery
import sources.bundesliga.queries.TabelleQuerySettings
import sources.bundesliga.queries.Top4TabellenQuerySettings
import sources.bundesliga.queries.Top5TabellenQuery

suspend fun main() {
    val bundesligaSettings = BundesligaSettings(
        lieblingsverein = "Mainz",
        liga = "bl1"
    )
    val bundesligaModule = BundesligaModule(bundesligaSettings)

    // Nächste Spiele Query
    val naechsteSpieleQuery = NaechsteSpieleQuery()
    val querySettings = NaechsteSpieleQuerySettings(
        anzahlSpiele = 3,
        nurHeimspiele = false
    )
    val result = naechsteSpieleQuery.execute(
        bundesligaModule.settings,
        querySettings
    )

    // Einzelne Liga Tabelle
    val tabelleQuery = TabelleQuery()
    val tabellenQuerySettings = TabelleQuerySettings(anzahlTeams = 18, nurLieblingsverein = false)
    val bundesligaTabelle = tabelleQuery.execute(
        bundesligaModule.settings,
        tabellenQuerySettings
    )

    // Top 5 Ligen Tabelle
    val top5Query = Top5TabellenQuery()
    val top5QuerySettings = Top4TabellenQuerySettings(anzahlTeamsProLiga = 100)
    val top5Tabelle = top5Query.execute(
        bundesligaModule.settings,
        top5QuerySettings
    )

    val messenger = BundesligaDiscordRenderer()

    // Ausgabe Einzelliga
    println("=== BUNDESLIGA TABELLE ===")
    if (bundesligaTabelle is QueryResult.Success) {
        println(messenger.createDiscordMessage(bundesligaTabelle.data))
    }

    // Ausgabe Top 5 Ligen
    println("\n=== TOP TEAMS ALLER LIGEN ===")
    when (top5Tabelle) {
        is QueryResult.Success -> println(messenger.createDiscordMessage(top5Tabelle.data))
        is QueryResult.Error -> println("Fehler: ${top5Tabelle.message}")
        is QueryResult.Loading -> println("Lädt...")
    }

    // Ausgabe Nächste Spiele
    println("\n=== NÄCHSTE SPIELE ===")
    when (result) {
        is QueryResult.Success -> println("Spiele: ${result.data}")
        is QueryResult.Error -> println("Fehler: ${result.message}")
        is QueryResult.Loading -> println("Lädt...")
    }
}