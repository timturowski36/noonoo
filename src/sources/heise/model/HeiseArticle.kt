package sources.heise.model

import java.time.Instant

data class HeiseArticle(
    val title: String,
    val link: String,
    val description: String,
    val pubDate: Instant,
    val guid: String,
    val contentHtml: String? = null,
    val isSponsored: Boolean = false
) {
    fun isNewerThan(other: HeiseArticle): Boolean = pubDate.isAfter(other.pubDate)

    fun containsKeyword(keyword: String): Boolean {
        val lowerKeyword = keyword.lowercase()
        return title.lowercase().contains(lowerKeyword) ||
               description.lowercase().contains(lowerKeyword)
    }
}
