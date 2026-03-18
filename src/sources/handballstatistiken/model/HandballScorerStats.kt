package sources.handballstatistiken.model

/**
 * Torjäger-Statistik eines einzelnen Spielers von handballstatistiken.de.
 *
 * Spalten der Tabelle (in Reihenfolge):
 *   #  Name  Mannschaft  TrNr.  Spiele  Tore  Feldtore  7m Tore  7m gew.  7m %
 *   letztes Spiel  Tore/Spiel  Feldt./Spiel  Verw.  2 Min.  Disq.
 */
data class HandballScorerStats(
    /** Tabellenplatz (nach Toren) */
    val rang: Int,
    /** Spielername */
    val name: String,
    /** Mannschaft */
    val mannschaft: String,
    /** Trikotnummer */
    val trNr: String,
    /** Gespielte Spiele */
    val spiele: Int,
    /** Gesamttore (Feld + 7m) */
    val tore: Int,
    /** Feldtore */
    val feldtore: Int,
    /** Erzielte 7-Meter-Tore */
    val siebenmeterTore: Int,
    /** Geworfene 7-Meter */
    val siebenmeterGeworfen: Int,
    /** 7-Meter-Trefferquote, z.B. "64.29%" */
    val siebenmeterProzent: String,
    /** Ergebnis/Datum des letzten Spiels, z.B. "6/4" oder "-" */
    val letztesSpiel: String,
    /** Tore pro Spiel */
    val toreProSpiel: Double,
    /** Feldtore pro Spiel */
    val feldtoreProSpiel: Double,
    /** Verwarnungen */
    val verwarnungen: Int,
    /** Zwei-Minuten-Strafen */
    val zweiMinuten: Int,
    /** Disqualifikationen */
    val disqualifikationen: Int
)
