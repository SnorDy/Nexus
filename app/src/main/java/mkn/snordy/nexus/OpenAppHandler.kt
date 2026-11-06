package mkn.snordy.nexus.commands

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class OpenAppHandler(
    private val context: Context,
    private val apps: AppRepository,
) : CommandHandler {

    override val intentName = "open_app"

    override suspend fun handle(intent: NexusIntent): CommandResult {
        val query = intent.slots["app"]?.takeIf { it.isNotBlank() }
            ?: return CommandResult.Failure("Не понял, какое приложение открыть")

        val found = apps.find(query)
        when {
            found.isEmpty() ->
                return CommandResult.Failure("Приложение «$query» не найдено")
            found.size > 1 ->
                return CommandResult.NeedsClarification(
                    "Какое открыть: ${found.joinToString(" или ") { it.label }}?"
                )
        }

        val app = found.single()
        val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return CommandResult.Failure("Не могу запустить ${app.label}")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW не выдано: запуск из фона может не сработать")
        }

        return try {
            context.startActivity(launch)
            CommandResult.Success("Открываю ${app.label}")
        } catch (e: Exception) {
            Log.e(TAG, "startActivity failed", e)
            CommandResult.Failure("Не удалось открыть ${app.label}")
        }
    }

    private companion object { const val TAG = "OpenAppHandler" }
}
