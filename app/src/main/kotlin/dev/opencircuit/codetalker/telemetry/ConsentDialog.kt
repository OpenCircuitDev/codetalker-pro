package dev.opencircuit.codetalker.telemetry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CCT-32 Task G.2 — UI surface for the crash-reporting consent dialog.
 *
 * The dialog is shown once after onboarding. Both buttons close the
 * dialog and persist the choice via [ConsentFlow]; the user can flip
 * the setting any time in PreferencesScreen afterwards.
 *
 * Default action is **decline** to align with the privacy policy
 * default-off invariant.
 */
@Composable
fun ConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Help improve codetalker?") },
        text = {
            Column {
                Text(
                    "If something crashes, send anonymous crash reports?",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "We send only stack traces, app version, and device model. No audio, transcripts, or session text are ever sent. You can change this any time in Preferences.",
                    fontSize = 12.sp,
                    color = Color(0xFFAAB1C0),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Sure, send reports")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("No thanks")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    )
}
