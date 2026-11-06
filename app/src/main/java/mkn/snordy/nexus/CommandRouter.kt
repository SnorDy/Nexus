package mkn.snordy.nexus.commands

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException

class CommandRouter(
    private val parsers: List<IntentParser>,
    handlers: List<CommandHandler>,
) {
    private val byName: Map<String, CommandHandler> = handlers.associateBy { it.intentName }

    suspend fun process(text: String): CommandResult {
        val intent = parsers.firstNotNullOfOrNull { it.parse(text) }
            ?: return CommandResult.Failure("Не понял команду: «$text»")

        val handler = byName[intent.name]
            ?: return CommandResult.Failure("Пока не умею: ${intent.name}")

        return try {
            handler.handle(intent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CommandRouter", "handler ${intent.name} failed", e)
            CommandResult.Failure("Ошибка при выполнении команды")
        }
    }
}
object NexusCommands {
    fun createRouter(context: Context): CommandRouter {
        val apps = AppRepository(context)
        return CommandRouter(
            parsers = listOf(
                RuleBasedIntentParser(),
            ),
            handlers = listOf(
                OpenAppHandler(context, apps),
            ),
        )
    }
}
