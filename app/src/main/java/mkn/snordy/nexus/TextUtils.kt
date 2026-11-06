package mkn.snordy.nexus.commands

fun normalizeRu(s: String): String =
    s.lowercase()
        .replace('ё', 'е')
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()

