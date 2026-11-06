package mkn.snordy.nexus.commands

class RuleBasedIntentParser : IntentParser {

    private class Rule(val intent: String, val regex: Regex, val slotNames: List<String>)

    private val rules: List<Rule> = listOf(

        // "открой ютуб", "запусти приложение телеграм", "пожалуйста открой камеру"
        Rule(
            intent = "open_app",
            regex = Regex("^(?:пожалуйста\\s+)?(?:открой|открыть|запусти|запустить)\\s+(?:приложение\\s+)?(.+)$"),
            slotNames = listOf("app"),
        ),
    )

    override suspend fun parse(text: String): NexusIntent? {
        val t = normalizeRu(text)
        if (t.isEmpty()) return null

        for (rule in rules) {
            val m = rule.regex.matchEntire(t) ?: continue
            val slots = rule.slotNames.mapIndexed { i, name -> name to m.groupValues[i + 1].trim() }.toMap()
            return NexusIntent(name = rule.intent, slots = slots, rawText = text)
        }
        return null
    }
}
