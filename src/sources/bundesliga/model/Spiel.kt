package sources.bundesliga.model

data class Spiel(
    val datum: String,
    val heimmannschaft: String,
    val gastmannschaft: String,
    val spieltag: Int
)