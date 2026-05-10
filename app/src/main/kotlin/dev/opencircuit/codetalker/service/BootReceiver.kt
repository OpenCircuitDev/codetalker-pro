package dev.opencircuit.codetalker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.opencircuit.codetalker.net.PairingFlow
import dev.opencircuit.codetalker.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * CCT-32 Task B.4 — opt-in start-on-boot receiver.
 *
 * Fires on BOOT_COMPLETED. We start [CompanionForegroundService] only
 * when both predicates hold:
 *   1. The user enabled "Start on device boot" via PreferencesScreen.
 *   2. There's a stored pairing — no point booting if we'd just sit on
 *      the pairing screen.
 *
 * Predicate (1) is read from DataStore; (2) is read from
 * EncryptedSharedPreferences. Both are safe from a BroadcastReceiver
 * because the work happens off-thread in a CoroutineScope.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        // We must finish onReceive synchronously, so use goAsync to keep
        // the receiver alive while the predicate IO runs.
        val pendingResult = goAsync()
        val app = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val optedIn = AppPreferences.forContext(app).startOnBoot.first()
                val paired = PairingFlow(app).current() != null
                if (optedIn && paired) {
                    CompanionForegroundService.start(app)
                }
            } catch (_: Throwable) {
                // Receiver path must never crash the system.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
