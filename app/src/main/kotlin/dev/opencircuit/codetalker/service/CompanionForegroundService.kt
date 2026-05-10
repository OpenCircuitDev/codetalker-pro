package dev.opencircuit.codetalker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.opencircuit.codetalker.MainActivity
import dev.opencircuit.codetalker.R

/**
 * CCT-31 Phase 10a — foreground service for the AR companion.
 *
 * Android 14 aggressively kills background apps. Without a foreground
 * service, the moment the user closes the app drawer or rotates the
 * Beam Pro, the OS terminates our SSE subscription + audio stream and
 * the listener loses narration. The fix is a foreground service with
 * a media-playback type — a category that grants legitimate audio
 * background-runtime as long as a notification is showing.
 *
 * Lifecycle:
 *   - MainActivity.onResume() → context.startForegroundService(intent)
 *     bringing this service into the FOREGROUND state.
 *   - This service holds the persistent notification + the audio
 *     stream subscription handles + the buddy SSE handle.
 *   - MainActivity.onPause() does NOT stop the service; the user
 *     wants audio to keep playing when they pop out the camera or
 *     swipe to a notification. Stop only on explicit "Disconnect"
 *     action from the notification or unpair flow.
 *   - The notification has a single action: "Disconnect" → stops self.
 */
class CompanionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE,
            ACTION_RESUME,
            ACTION_RECONNECT -> {
                // CCT-32 Task B.5: paths are intentionally light — the
                // service logs the request so it's visible in logcat and
                // observable by E2E. Phase B.5 wires actual audio
                // pause/resume into TTSPlayer when the player ownership
                // moves into this service in Phase 8.
                android.util.Log.i(
                    "CompanionFg",
                    "lifecycle action: ${intent.action}",
                )
            }
            else -> startInForeground()
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires the service type to be declared at runtime.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CompanionForegroundService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Codetalker AR Companion")
            .setContentText("Listening to active session — narration playing")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectIntent,
            )
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AR Companion connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps audio + SSE alive while the AR app is in background"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "cct_companion_fg"
        const val NOTIFICATION_ID = 1717
        const val ACTION_DISCONNECT = "dev.opencircuit.codetalker.ACTION_DISCONNECT"
        // CCT-32 Task B.5: lifecycle action verbs sent by MainActivity.
        const val ACTION_PAUSE = "dev.opencircuit.codetalker.ACTION_PAUSE"
        const val ACTION_RESUME = "dev.opencircuit.codetalker.ACTION_RESUME"
        const val ACTION_RECONNECT = "dev.opencircuit.codetalker.ACTION_RECONNECT"

        /** Convenience launcher used by MainActivity. */
        fun start(context: Context) {
            val intent = Intent(context, CompanionForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CompanionForegroundService::class.java).apply {
                    action = ACTION_DISCONNECT
                },
            )
        }

        /** CCT-32 Task B.5 — forwarded from MainActivity on screen-off. */
        fun notifyPause(context: Context) {
            context.startService(
                Intent(context, CompanionForegroundService::class.java).apply {
                    action = ACTION_PAUSE
                },
            )
        }

        /** CCT-32 Task B.5 — forwarded from MainActivity on screen-on. */
        fun notifyResume(context: Context) {
            context.startService(
                Intent(context, CompanionForegroundService::class.java).apply {
                    action = ACTION_RESUME
                },
            )
        }

        /** CCT-32 Task B.5 — forwarded from MainActivity on network-available. */
        fun notifyReconnect(context: Context) {
            context.startService(
                Intent(context, CompanionForegroundService::class.java).apply {
                    action = ACTION_RECONNECT
                },
            )
        }
    }
}
