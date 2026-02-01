package sources.bundesliga.model

data class TabellenEintrag(
    val liga: String,
    val platz: Int,
    val teamInfoId: Int,
    val teamName: String,
    val shortName: String,
    val points: Int,
    val opponentGoals: Int,
    val goals: Int,
    val matches: Int,
    val won: Int,
    val lost: Int,
    val draw: Int,
    val goalDiff: Int
)