package com.pantelmg.tb2cal.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Settings
import com.pantelmg.tb2cal.model.Vibration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays the completion alert itself (instead of through the notification channel), so it can:
 * Pulse until stopped, use the media or the notification volume, and play any chosen sound.
 */
class AlertService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var test = false
    private var label = ""
    private var blockId: String? = null
    private var soundDone = true
    private var vibeDone = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Repo.init(this)
        if (intent?.action == ACTION_STOP) {
            if (running) finish(keepNotification = intent.getBooleanExtra(EXTRA_KEEP, true)) else stopSelf()
            return START_NOT_STICKY
        }
        test = intent?.getBooleanExtra(EXTRA_TEST, false) == true
        testActive = test
        label = intent?.getStringExtra(EXTRA_LABEL) ?: ""
        blockId = intent?.getStringExtra(EXTRA_ID)
        val n = Notifications.completed(this, label, test, alerting = true, id = blockId)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifications.COMPLETE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notifications.COMPLETE_ID, n)
            }
        } catch (e: Exception) {
            Notifications.post(this, Notifications.COMPLETE_ID, Notifications.completed(this, label, test, false, blockId))
            releaseWake()
            stopSelf()
            return START_NOT_STICKY
        }
        _running.value = true
        startAlert(Repo.settings.value)
        return START_NOT_STICKY
    }

    private fun startAlert(s: Settings) {
        stopOutputs()
        handler.removeCallbacksAndMessages(null)

        // Sound: Played once, at the media or the notification volume.
        val uri: Uri? = when (val u = s.soundUri) {
            "" -> null
            null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> Uri.parse(u)
        }
        val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // If the chosen sound can't be played any more, use the default sound instead of silence.
        player = uri?.let { u -> createPlayer(u, s) ?: if (u != default) createPlayer(default, s) else null }
        soundDone = player == null

        // Vibration: Once, or repeating until stopped.
        vibeDone = true
        val pattern = s.vibration.pattern
        if (pattern != null) {
            vibrator = vibratorOf(this)?.takeIf { it.hasVibrator() }
            vibrator?.let { v ->
                vibrate(v, VibrationEffect.createWaveform(pattern, if (s.vibration.repeats) 0 else -1))
                vibeDone = false
                if (!s.vibration.repeats) {
                    handler.postDelayed({
                        vibeDone = true
                        maybeFinish()
                    }, pattern.sum())
                }
            }
        }

        // Safety cap so a forgotten alert can't run forever.
        handler.postDelayed({ finish(keepNotification = !test) }, if (s.vibration.repeats) MAX_REPEAT_MS else MAX_ONCE_MS)
        maybeFinish()
    }

    private fun createPlayer(u: Uri, s: Settings): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        if (s.useMediaVolume) AudioAttributes.USAGE_MEDIA
                        else AudioAttributes.USAGE_NOTIFICATION_EVENT
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlertService, u)
            setOnCompletionListener {
                soundDone = true
                maybeFinish()
            }
            setOnErrorListener { _, _, _ ->
                soundDone = true
                maybeFinish()
                true
            }
            prepare()
            start()
        }
    }.getOrNull()

    private fun maybeFinish() {
        if (running && soundDone && vibeDone) finish(keepNotification = !test)
    }

    private fun finish(keepNotification: Boolean) {
        handler.removeCallbacksAndMessages(null)
        stopOutputs()
        _running.value = false
        ServiceCompat.stopForeground(
            this,
            if (keepNotification) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE
        )
        if (keepNotification && !test) {
            // Same notification without the "Stop alert" action.
            Notifications.post(this, Notifications.COMPLETE_ID, Notifications.completed(this, label, false, alerting = false, id = blockId))
        }
        releaseWake()
        stopSelf()
    }

    private fun stopOutputs() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopOutputs()
        _running.value = false
        releaseWake()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.pantelmg.tb2cal.alert.STOP"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TEST = "test"
        private const val EXTRA_KEEP = "keep"
        private const val MAX_ONCE_MS = 60_000L
        private const val MAX_REPEAT_MS = 10 * 60_000L

        private val _running = MutableStateFlow(false)
        /** Observed by Settings to switch "Test alert" to "Stop alert". */
        val runningFlow: StateFlow<Boolean> = _running.asStateFlow()
        val running: Boolean get() = _running.value

        private var wake: PowerManager.WakeLock? = null

        /** Starts the alert. Safe to call from receivers, services and the UI. */
        fun start(ctx: Context, label: String, test: Boolean = false, id: String? = null) {
            acquireWake(ctx)
            val i = Intent(ctx, AlertService::class.java)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TEST, test)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                // Background start refused: At least show the notification.
                Notifications.post(ctx, Notifications.COMPLETE_ID, Notifications.completed(ctx, label, test, false, id))
                releaseWake()
            }
        }

        /** True while the running alert is a "Test alert" from Settings. */
        @Volatile
        var testActive = false
            private set

        /** Stops the alert only if it's a test (leaving Settings); A real TimeBlock alert keeps going. */
        fun stopTest(ctx: Context) {
            if (running && testActive) stop(ctx, keepNotification = false)
        }

        /** Silences a running alert. With keepNotification = false, the notification is removed too. */
        fun stop(ctx: Context, keepNotification: Boolean) {
            if (!running) return
            runCatching {
                ctx.startService(
                    Intent(ctx, AlertService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_KEEP, keepNotification)
                )
            }
        }

        fun stopIntent(ctx: Context, keepNotification: Boolean): PendingIntent = PendingIntent.getService(
            ctx, if (keepNotification) 11 else 12,
            Intent(ctx, AlertService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_KEEP, keepNotification),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        @Synchronized
        private fun acquireWake(ctx: Context) {
            if (wake?.isHeld == true) return
            wake = ctx.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tb2cal:alert")
                ?.apply {
                    setReferenceCounted(false)
                    acquire(MAX_REPEAT_MS + 60_000L)
                }
        }

        @Synchronized
        private fun releaseWake() {
            wake?.let { if (it.isHeld) it.release() }
            wake = null
        }

        @Suppress("DEPRECATION")
        fun vibratorOf(ctx: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                ctx.getSystemService(Vibrator::class.java)
            }

        /** Alarm-class vibration, so it isn't muted like ordinary notification vibration. */
        @Suppress("DEPRECATION")
        fun vibrate(v: Vibrator, effect: VibrationEffect) {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                v.vibrate(
                    effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
            }
        }

        /** One-shot preview for the settings screen. */
        fun preview(ctx: Context, vib: Vibration) {
            val p = vib.pattern ?: return
            vibratorOf(ctx)?.let { vibrate(it, VibrationEffect.createWaveform(p, -1)) }
        }
    }
}
