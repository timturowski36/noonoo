package sources.heise.model

enum class HeiseFeed(val url: String, val displayName: String) {
    ALLE("https://www.heise.de/rss/heise.rdf", "Heise Online"),
    SECURITY("https://www.heise.de/security/rss/news.rdf", "Heise Security"),
    DEVELOPER("https://www.heise.de/developer/rss/news.rdf", "Heise Developer"),
    IX("https://www.heise.de/ix/rss/news.rdf", "iX Magazin"),
    CT("https://www.heise.de/ct/rss/news.rdf", "c't Magazin"),
    AUTOS("https://www.heise.de/autos/rss/news.rdf", "Heise Autos"),
    TELEPOLIS("https://www.heise.de/tp/rss/news.rdf", "Telepolis"),
    MAC_AND_I("https://www.heise.de/mac-and-i/rss/news.rdf", "Mac & i")
}
