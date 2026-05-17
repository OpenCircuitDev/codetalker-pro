package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import dev.opencircuit.codetalker.qr.QrScannerScreen

/**
 * CCT-31 Phase 5c — pairing screen with both QR scan and manual entry.
 *
 * QR mode invokes [QrScannerScreen] which uses CameraX + ZXing to find
 * a JSON payload like {"daemon_url":"...","pairing_token":"..."}. On scan,
 * we hand it to [PairingFlow.savePairing] for validation + persistence.
 *
 * Manual mode shows the URL + token fields. Users can also paste the entire
 * QR JSON into the URL field — savePairing handles both formats.
 */
@Composable
fun PairingScreen(
    pairingFlow: PairingFlow,
    onPaired: (Pairing) -> Unit,
) {
    var mode by remember { mutableStateOf(PairingMode.Choose) }

    when (mode) {
        PairingMode.Choose -> ChooseModeScreen(
            onChooseQr = { mode = PairingMode.Qr },
            onChooseManual = { mode = PairingMode.Manual },
        )
        PairingMode.Qr -> QrScannerScreen(
            onScanned = { payload ->
                try {
                    val paired = pairingFlow.savePairing(payload)
                    onPaired(paired)
                } catch (_: Throwable) {
                    // Bad QR payload — fall through to manual so the user can try.
                    mode = PairingMode.Manual
                }
            },
            onCancel = { mode = PairingMode.Manual },
        )
        PairingMode.Manual -> ManualEntryScreen(
            pairingFlow = pairingFlow,
            onPaired = onPaired,
            onSwitchToQr = { mode = PairingMode.Qr },
        )
    }
}

private enum class PairingMode { Choose, Qr, Manual }

@Composable
private fun ChooseModeScreen(
    onChooseQr: () -> Unit,
    onChooseManual: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Pair with codetalker daemon", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Open the dashboard's Preferences → AR Companion panel and use the QR code or copy the daemon URL + token.",
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onChooseQr, modifier = Modifier.fillMaxWidth()) {
            Text("Scan QR code")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onChooseManual, modifier = Modifier.fillMaxWidth()) {
            Text("Enter manually")
        }
    }
}

@Composable
private fun ManualEntryScreen(
    pairingFlow: PairingFlow,
    onPaired: (Pairing) -> Unit,
    onSwitchToQr: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Manual pairing", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Paste the daemon URL + pairing token from the dashboard. You can also paste the whole QR JSON into the URL field.",
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = null },
            label = { Text("Daemon URL") },
            placeholder = { Text("http://192.168.1.86:17832") },
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

        Row {
            Button(
                onClick = {
                    try {
                        val trimmed = url.trim()
                        val paired = if (trimmed.startsWith("{")) {
                            pairingFlow.savePairing(trimmed)
                        } else {
                            val normalized = when {
                                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                                trimmed.isNotEmpty() -> "http://$trimmed"
                                else -> trimmed
                            }
                            pairingFlow.saveManual(normalized, token.trim())
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
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onSwitchToQr) { Text("Use QR instead") }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text("Error: $it", fontSize = 13.sp)
        }
    }
}
