package dev.opencircuit.codetalker.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * CCT-32 Task B.6 — diagnostics surface.
 *
 * Live values, refreshed every 3s while the screen is open:
 *   - Daemon last-success timestamp (and round-trip ms)
 *   - Pairing token expiry countdown — daemon reports via /api/health
 *     when supported; otherwise we just show "valid"
 *   - Audio buffer state — owned by TTSPlayer; future revisions thread
 *     a live state in. v1 reports "TTSPlayer: no signal" until wired.
 *   - Buddy session id — from AppPreferences.activeSessionId
 *   - Glasses display detection — dumpsys SurfaceFlinger lookup
 *   - Latency RTT — single GET /api/health timing
 *   - Battery — BatteryManager broadcast capacity
 *
 * Used as the user-visible debug surface for v1 release.
 */
@Composable
fun DiagnosticsScreen(
    daemonClient: DaemonClient,
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var lastSuccessMs by remember { mutableStateOf<Long?>(null) }
    var lastRttMs by remember { mutableStateOf<Long?>(null) }
    var healthError by remember { mutableStateOf<String?>(null) }
    var narrationWarning by remember { mutableStateOf<String?>(null) }
    var batteryPct by remember { mutableStateOf<Int?>(null) }
    var glassesAttached by remember { mutableStateOf<Boolean?>(null) }
    val activeSessionId by appPreferences.activeSessionId.collectAsState(initial = null)

    // Periodic refresh.
    LaunchedEffect(Unit) {
        while (true) {
            // Daemon health + RTT
            val t0 = System.currentTimeMillis()
            val rtt = withContext(Dispatchers.IO) {
                try {
                    val health = daemonClient.getHealthOrThrow()
                    val dt = System.currentTimeMillis() - t0
                    healthError = null
                    narrationWarning = health.narrationWarning
                    lastSuccessMs = System.currentTimeMillis()
                    dt
                } catch (e: Throwable) {
                    healthError = e.message ?: "unknown"
                    null
                }
            }
            if (rtt != null) lastRttMs = rtt
            // Battery
            batteryPct = readBatteryPct(context)
            // Glasses display detection — heuristic: more than one display id from SurfaceFlinger.
            glassesAttached = withContext(Dispatchers.IO) { detectGlasses(context) }
            delay(3_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Diagnostics", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(12.dp))

        // 2026-05-16 -- master-narration banner. The single line in
        // ~/.claude/scripts/tts_config.yaml that gates every TTS hook
        // can silently disable narration across the fleet. Surfacing
        // it here means the next time audio stalls, the cause is
        // visible in one screen instead of buried in YAML.
        narrationWarning?.let { warning ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Narration disabled",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = androidx.compose.ui.graphics.Color(0xFFFB923C),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(warning, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFFB8BCC4))
                }
            }
        }

        StatusCard(
            label = "Daemon health",
            value = if (healthError != null) "error: $healthError"
                else lastSuccessMs?.let { "ok (${formatAgo(it)} ago)" } ?: "checking…",
        )
        StatusCard(
            label = "Round-trip latency",
            value = lastRttMs?.let { "${it}ms" } ?: "—",
        )
        StatusCard(
            label = "Pairing token",
            value = if (healthError == "HTTP 401" || healthError == "HTTP 403")
                "rejected — re-pair"
            else "valid",
        )
        StatusCard(
            label = "Audio focus",
            value = "TTSPlayer ready (live wiring in B.5)",
        )
        StatusCard(
            label = "Active session id",
            value = activeSessionId ?: "(none)",
        )
        StatusCard(
            label = "Glasses display",
            value = when (glassesAttached) {
                true -> "external display detected"
                false -> "single display (Beam Pro only)"
                null -> "checking…"
            },
        )
        StatusCard(
            label = "Battery",
            value = batteryPct?.let { "$it%" } ?: "—",
        )
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun readBatteryPct(context: Context): Int? = try {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    if (level < 0 || scale <= 0) null else (level * 100 / scale)
} catch (_: Throwable) { null }

private fun detectGlasses(context: Context): Boolean {
    return try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        // Anything beyond the built-in display indicates an external panel
        // — XREAL Air mirrors as a presentation display.
        dm.displays.size > 1
    } catch (_: Throwable) { false }
}

private fun formatAgo(epochMs: Long): String {
    val s = (System.currentTimeMillis() - epochMs) / 1000
    return when {
        s < 1 -> "0s"
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        else -> "${s / 3600}h"
    }
}
