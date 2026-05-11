package com.pg_axis.ytcnv.utils

object StringUtils {

    fun cleanUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        var cleaned = url.trim()
        listOf("https://", "http://", "www.", "m.", "music.", "youtube.com/", "youtu.be/", "shorts/", "watch?v=")
            .forEach { cleaned = cleaned.replace(it, "", ignoreCase = true) }
        if (cleaned.length < 11) return ""
        return cleaned.substring(0, 11)
    }

    fun cleanAuthor(author: String): String {
        return author
            .replace(Regex("(VEVO|OfficialVEVO|TV|- Topic)$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun cleanTitle(title: String, author: String): Pair<String, String> {
        var t = title.trim()
        var a = cleanAuthor(author)

        val toRemove = listOf(
            "Official Music Video", "Official Video", "Lyric Video", "Lyrics Video",
            "Official Audio", "Audio", "Official Audio Visualizer", "Official Song",
            "Full Album", "Deluxe Edition", "Lyrics"
        )

        // Strip invalid filename chars
        t = t.filter { it !in "/\\:*?\"<>|" }

        // Remove common suffixes in brackets
        toRemove.forEach { sub ->
            t = t.replace("($sub)", "", ignoreCase = true)
            t = t.replace("[$sub]", "", ignoreCase = true)
        }

        // Remove square bracket content
        t = t.replace(Regex("\\[.*?]"), "")

        // Remove author from title
        t = t.replace("$a - ", "", ignoreCase = true)
        t = t.replace("$a-", "", ignoreCase = true)
        t = t.replace(" - $a", "", ignoreCase = true)
        t = t.replace("-$a", "", ignoreCase = true)

        // Try to extract author from "Artist - Title" pattern
        val parts = t.split(" - ")
        if (parts.size > 1) {
            for (i in parts.indices) {
                val normalizedPart = parts[i].replace(" ", "")
                val normalizedAuthor = a.replace(" ", "")
                if (normalizedPart.contains(normalizedAuthor, ignoreCase = true)) {
                    a = parts[i].trim()
                    t = parts.filterIndexed { index, _ -> index != i }.joinToString(" - ")
                    break
                }
            }
        } else {
            val subParts = t.split("-")
            if (subParts.size > 1) {
                for (i in subParts.indices) {
                    val normalizedPart = subParts[i].replace(" ", "")
                    val normalizedAuthor = a.replace(" ", "")
                    if (normalizedPart.contains(normalizedAuthor, ignoreCase = true)) {
                        a = subParts[i].trim()
                        t = subParts.filterIndexed { index, _ -> index != i }.joinToString(" - ")
                        break
                    }
                }
            }
        }

        t = t.trim()
        if (t.isBlank()) t = "YouTube_Video"
        if (t.length > 60) t = truncateSmart(t)

        return Pair(t, a)
    }

    fun truncateSmart(input: String, maxLength: Int = 60): String {
        if (input.length <= maxLength) return input
        val lastSpace = input.lastIndexOf(' ', maxLength)
        return if (lastSpace > -1) input.substring(0, lastSpace) + " ..."
        else input.substring(0, maxLength) + " ..."
    }
}