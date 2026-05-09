package dev.opencircuit.codetalker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * CCT-31 — Codetalker AR Companion entry point.
 *
 * v1 Phase 5 hosts the pairing flow + the basic HUD. Later phases (8+)
 * will swap this for the XREAL Nebula AR composition root.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompanionRoot()
            }
        }
    }
}

@Composable
private fun CompanionRoot() {
    // Phase 5 stub. Phase 5b will introduce a NavHost: PairingScreen ->
    // SessionListScreen -> AROverlay. For now this just renders a placeholder
    // so the project builds.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B10))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Codetalker AR Companion\n(Phase 5 scaffold)",
            color = Color(0xFFE6E8EE),
        )
    }
}
