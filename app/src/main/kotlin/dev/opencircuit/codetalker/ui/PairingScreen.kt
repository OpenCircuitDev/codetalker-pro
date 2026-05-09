package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.net.Pairing
import dev.opencircuit.codetalker.net.PairingFlow

/**
 * CCT-31 Phase 5c — manual-entry pairing screen.
 *
 * QR scanning lands in a follow-up commit (CameraX + ZXing). For v1 we
 * support manual entry — paste the daemon URL and token from the dashboard's
 * "Pair AR Companion" panel. This unblocks Phases 6+7 testing without
 * waiting on camera permission UX.
 *
 * The dashboard's QR payload is already a JSON object users can paste
 * verbatim into the URL field if they're feeling lazy:
 *   {"daemon_url":"http://...","pairing_token":"..."}
 * — we fall back to parsing JSON when the URL field starts with "{".
 */
@Composable
fun PairingScreen(
    pairingFlow: PairingFlow,
    onPaired: (Pairing) -> Unit,
) {
    var url by remember { mutableStateOf("http://") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Pair with codetalker daemon",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Open the dashboard at the daemon's address (default http://localhost:17832/ui-react/), go to Preferences → AR Companion, click Issue pairing token, then paste the values below. You can also paste the entire QR JSON into the URL field.",
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = null },
            label = { Text("Daemon URL") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; error = null },
            label = { Text("Pairing token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                try {
                    val paired = if (url.trim().startsWith("{")) {
                        // The user pasted the full QR JSON into the URL field.
                        pairingFlow.savePairing(url.trim())
                    } else {
                        pairingFlow.saveManual(url.trim(), token.trim())
                    }
                    onPaired(paired)
                } catch (e: Throwable) {
                    error = e.message ?: "pairing failed"
                }
            },
            modifier = Modifier.width(160.dp),
        ) {
            Text("Save")
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text("Error: $it", fontSize = 13.sp)
        }
    }
}
