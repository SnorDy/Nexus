package mkn.snordy.nexus.commands

data class NexusIntent(
    val name: String,
    val slots: Map<String, String> = emptyMap(),
    val rawText: String,
)

sealed interface CommandResult {
    data class Success(val message: String) : CommandResult
    data class Failure(val message: String) : CommandResult
    data class NeedsClarification(val question: String) : CommandResult
}
fun interface IntentParser {
    suspend fun parse(text: String): NexusIntent?
}

interface CommandHandler {
    val intentName: String
    suspend fun handle(intent: NexusIntent): CommandResult
}
