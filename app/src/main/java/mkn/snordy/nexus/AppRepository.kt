package mkn.snordy.nexus.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import mkn.snordy.nexus.WakeWordMatcher.lev

data class AppInfo(val label: String, val packageName: String)

class AppRepository(private val context: Context) {

    private class Entry(val app: AppInfo, val keys: List<String>)

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var loadedAt = 0L

    private val spokenAliases: Map<String, List<String>> = mapOf(
        "com.google.android.youtube" to listOf("ютуб", "ютюб"),
        "com.google.android.apps.youtube.music" to listOf("ютуб музыка", "ютуб мьюзик"),
        "org.telegram.messenger" to listOf("телеграм", "телега", "телеграмм", "тг"),
        "com.vkontakte.android" to listOf("вк", "вконтакте", "контакте"),
        "com.whatsapp" to listOf("ватсап", "вотсап", "вацап", "ватсапп"),
        "ru.yandex.music" to listOf("яндекс музыка", "яндекс музыку"),
        "ru.yandex.yandexmaps" to listOf("яндекс карты"),
        "com.spotify.music" to listOf("спотифай", "спотик"),
        "com.android.chrome" to listOf("хром", "гугл хром"),
        "com.instagram.android" to listOf("инстаграм", "инста"),
        "com.yandex.browser" to listOf("яндекс", "браузер", "яндекс браузер"),
        "ru.ozon.app.android" to listOf("азон", "озон"),
        "com.wildberries.ru" to listOf("вб", "вайлдберис", "вайлдбериз", "валдберис","валдбериз"),
        "com.deepseek.chat" to listOf("дипсик"),
        "com.google.android" to listOf("почту", "почта", "гмайл"),
    )
    fun find(spoken: String): List<AppInfo> {
        val q = normalizeRu(spoken)
        if (q.isEmpty()) return emptyList()

        ensureLoaded()
        var result = search(q)
        if (result.isEmpty() && SystemClock.elapsedRealtime() - loadedAt > RELOAD_MIN_INTERVAL_MS) {
            load()
            result = search(q)
        }
        return result
    }

    private fun search(q: String): List<AppInfo> {
        var best = NO_MATCH
        val hits = ArrayList<AppInfo>()
        for (e in entries) {
            val s = e.keys.minOf { score(q, it) }
            if (s == NO_MATCH) continue
            if (s < best) { best = s; hits.clear(); hits.add(e.app) }
            else if (s == best) hits.add(e.app)
        }
        return hits
    }

    private fun score(q: String, key: String): Int {
        if (q == key) return 0
        if (minOf(q.length, key.length) >= 3 && (key.startsWith(q) || q.startsWith(key))) return 1
        val d = lev(q, key)
        val limit = maxOf(1, key.length / 4)
        return if (d <= limit) 1 + d else NO_MATCH
    }

    private fun ensureLoaded() {
        if (entries.isEmpty()) load()
    }

    @Synchronized
    private fun load() {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(launcher, 0)
        }

        entries = infos
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val label = ri.loadLabel(pm).toString()
                val keys = buildList {
                    add(normalizeRu(label))
                    spokenAliases[pkg]?.forEach { add(normalizeRu(it)) }
                }.filter { it.isNotEmpty() }.distinct()
                Entry(AppInfo(label, pkg), keys)
            }
        loadedAt = SystemClock.elapsedRealtime()
        Log.d(TAG, "loaded ${entries.size} launchable apps")
    }


    private companion object {
        const val TAG = "AppRepository"
        const val NO_MATCH = Int.MAX_VALUE
        const val RELOAD_MIN_INTERVAL_MS = 30_000L


    }
}
