package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.prefs.AppPreferences
import kotlinx.coroutines.launch

/**
 * CCT-32 Task B.4 — preferences surface.
 *
 * v1 holds the "Start on device boot" opt-in. Phase B.6 adds the
 * Diagnostics entry point (long-press menu); for now this screen is the
 * minimum needed to flip B.4 on.
 */
@Composable
fun PreferencesScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
    onOpenDiagnostics: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val startOnBoot by appPreferences.startOnBoot.collectAsState(initial = false)
    // CCT-32 Task G.2 — opt-in crash reporting toggle. ConsentFlow asks
    // once at first launch; this surfaces the setting forever after so
    // the user can flip it.
    val crashReportingEnabled by appPreferences.crashReportingEnabled.collectAsState(initial = false)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Preferences", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(Modifier.fillMaxWidth(0.8f)) {
                Text("Start on device boot", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "When the glasses power on, automatically reconnect to the daemon if you've already paired.",
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = startOnBoot,
                onCheckedChange = { newValue ->
                    scope.launch { appPreferences.setStartOnBoot(newValue) }
                },
            )
        }

        // CCT-32 Task G.2 — crash-report toggle (opt-in only).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(Modifier.fillMaxWidth(0.8f)) {
                Text(
                    "Send anonymous crash reports",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Help improve stability by sharing stack traces and device model only. No audio, transcripts, or session text are ever sent.",
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = crashReportingEnabled,
                onCheckedChange = { newValue ->
                    scope.launch { appPreferences.setCrashReportingEnabled(newValue) }
                },
            )
        }

        if (onOpenDiagnostics != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Diagnostics") }
        }
        if (onOpenAbout != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("About this app") }
        }
    }
}
