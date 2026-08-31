package com.koukishiba.todobookmark

/** 共有された文字列から HTTP / HTTPS URL を抽出し、出現順を保って重複を除く。 */
object ShareIntentParser {
    private val urlPattern = Regex(
        pattern = """https?://[^\s<>\"'）」「』】【、。]+""",
        option = RegexOption.IGNORE_CASE,
    )

    private val trailingDelimiters = setOf(',', '.')

    fun extractUrls(texts: Iterable<String>): List<String> = buildList {
        texts.forEach { text ->
            urlPattern.findAll(text).forEach { match ->
                val url = match.value.trim().trimEnd { it in trailingDelimiters }
                if (url.isNotEmpty()) {
                    add(url)
                }
            }
        }
    }.distinct()
}

