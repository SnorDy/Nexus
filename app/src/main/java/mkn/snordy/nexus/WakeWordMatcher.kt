package mkn.snordy.nexus

object WakeWordMatcher {
    private const val WAKE = "нексус"
    private val ALIASES = setOf("нексос", "нексус", "некзус", "наксус", "нексас","на иксус", "иисус", "иксус")
    private val BLOCK = setOf("лексус")

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').replace('э', 'е')
        .filter { it in 'а'..'я' || it in 'a'..'z' || it.isDigit() }

    fun match(text: String, maxPos: Int = 3, maxDist: Int = 1): String? {
        val raw = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val n = raw.map(::norm)
        for (i in 0 until minOf(n.size, maxPos + 1)) {
            if (isWake(n[i], maxDist)) return raw.drop(i + 1).joinToString(" ")
            if (i + 1 < n.size && isWake(n[i] + n[i + 1], maxDist))   // «некс ус»
                return raw.drop(i + 2).joinToString(" ")
        }
        return null
    }

    private fun isWake(w: String, maxDist: Int) =
        w !in BLOCK && (w in ALIASES || lev(w, WAKE) <= maxDist)

    //редакционное расстояние (вычисляет с помощью дп расстояние от слова нексус до переданного, допускается 1 ошибка)
    fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }
}