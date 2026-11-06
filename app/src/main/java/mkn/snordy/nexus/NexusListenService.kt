package mkn.snordy.nexus

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mkn.snordy.nexus.commands.CommandResult
import mkn.snordy.nexus.commands.CommandRouter
import mkn.snordy.nexus.commands.NexusCommands


class NexusListenService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listenJob: Job? = null
    private var resetJob: Job? = null

    private lateinit var router: CommandRouter

    private var wakeLock: PowerManager.WakeLock? = null //эта штука будет периодически пинать процессор, чтобы в спящем режиме он не погрузился в глубокий сон и все еще обрабатывал команды

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        router = NexusCommands.createRouter(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {  // обработка кнопки "Стоп" из шторки
            stopListening()
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            NexusState.set(NexusStatus.Error)
            stopSelf()
            return START_NOT_STICKY //не перезапускаем сервис, если система убьет его из-за нехватки памяти
        }

        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            NexusState.set(NexusStatus.Error)
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (listenJob?.isActive == true) return

        if (USE_WAKE_LOCK) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nexus:listen")
                .apply { setReferenceCounted(false); acquire() }
        }

        listenJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)//дает максимум процессорного времени, чтобы пакеты аудио не терялись
            NexusState.set(NexusStatus.Loading)
            Log.d("NEXUS STATE", NexusState.status.value.name)

            val pipeline = try {
                VadAsrPipeline(applicationContext, scope, listener)
            } catch (t: Throwable) {
                Log.e(TAG, "pipeline init failed", t)
                NexusState.set(NexusStatus.Error)
                return@launch
            }

            try {
                var failures = 0
                while (isActive && failures < MAX_FAILURES) {
                    val frames = recordLoop(pipeline)
                    if (!isActive) break
                    // Если запись продержалась хотя бы 3 секунды, считаем, что она работала нормально.
                    failures = if (frames > 100) 0 else failures + 1
                    Log.w(TAG, "record loop ended (frames=$frames), retry in 2s")
                    delay(2000)
                }
                if (isActive) NexusState.set(NexusStatus.Error)
            } finally {
                pipeline.close()
            }
        }
    }

    //один сеанс записи, возвращает сколько кадров успели прочитать
    @SuppressLint("MissingPermission") // RECORD_AUDIO проверен в onStartCommand
    private suspend fun recordLoop(pipeline: VadAsrPipeline): Int {
        val frame = VadAsrPipeline.FRAME
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return 0

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, frame * 2 * 8)
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord create failed", e)
            return 0
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return 0
        }

        val buf = ShortArray(frame)
        var frames = 0
        try {
            record.startRecording()
            NexusState.set(NexusStatus.Listening)

            val ctx = currentCoroutineContext()
            while (ctx.isActive) {
                var read = 0
                while (read < frame && ctx.isActive) {
                    val n = record.read(buf, read, frame - read)
                    if (n < 0) throw IllegalStateException("AudioRecord error $n")
                    read += n
                }
                if (read < frame) break
                pipeline.feed(buf)
                frames++
            }
        } catch (e: Exception) {
            Log.e(TAG, "record loop error", e)
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        return frames
    }


    private val listener = object : NexusListener {
        override fun onWakeOnly() {
            NexusState.set(NexusStatus.WakeDetected)
            scheduleReset(5500)          // окно ожидания команды в конвейере
        }

        override fun onCommand(text: String) {
            NexusState.set(NexusStatus.WakeDetected)
            NexusState.setCommand(text)
            handleCommand(text)
            scheduleReset(1500)
        }
    }


    private fun handleCommand(text: String) {
        scope.launch {
            val result = router.process(text)
            val msg = when (result) {
                is CommandResult.Success -> result.message
                is CommandResult.Failure -> result.message
                is CommandResult.NeedsClarification -> result.question
            }
            NexusState.setResult(msg)
            Log.d("RESULT", "result: $msg")
        }
        Log.d("CMD", "command: $text")
    }

    private fun scheduleReset(delayMs: Long) {
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(delayMs)
            if (NexusState.status.value == NexusStatus.WakeDetected) {
                NexusState.set(NexusStatus.Listening)
            }
        }
    }

    private fun stopListening() {
        resetJob?.cancel()
        listenJob?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        NexusState.set(NexusStatus.Stopped)
    }

    override fun onDestroy() {
        stopListening()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Nexus", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, NexusListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Nexus слушает")
            .setContentText("Ожидание команды «Нексус»")
            .setContentIntent(open)
            .addAction(0, "Остановить", stop)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.example.nexus.STOP"
        private const val TAG = "NexusService"
        private const val CHANNEL_ID = "nexus_listen"
        private const val NOTIF_ID = 1
        private const val SAMPLE_RATE = 16000
        private const val MAX_FAILURES = 5
        private const val USE_WAKE_LOCK = false
    }
}