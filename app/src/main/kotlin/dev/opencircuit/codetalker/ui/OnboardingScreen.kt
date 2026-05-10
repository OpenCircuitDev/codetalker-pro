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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CCT-32 Task B.2 — first-launch onboarding flow.
 *
 * Three pages:
 *   0. Welcome — what the app does, ask user to put on glasses if ready.
 *   1. Daemon setup — explains the QR scan + the dashboard side.
 *   2. Permissions intro — briefs CAMERA + MIC + NOTIFICATIONS before
 *      handing off to PermissionGate.
 *
 * The "completed" flag is persisted by the caller; this composable just
 * walks the pages and fires [onComplete] when done. Skip is offered
 * everywhere because users who already paired through the QR don't need
 * the tour every time.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }

    when (page) {
        0 -> WelcomePage(
            onNext = { page = 1 },
            onSkip = onComplete,
        )
        1 -> DaemonPage(
            onBack = { page = 0 },
            onNext = { page = 2 },
            onSkip = onComplete,
        )
        else -> PermissionsPage(
            onBack = { page = 1 },
            onNext = onComplete,
        )
    }
}

@Composable
private fun WelcomePage(
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardingPage(
        title = "Welcome to Codetalker",
        body = "Codetalker is your AR companion for Claude Code. " +
            "While Claude codes on your desktop, the glasses narrate what's happening — " +
            "in your chosen voice, with the character you've attached. " +
            "Hold the side button to talk back, release to send.",
        nextLabel = "Get started",
        onNext = onNext,
        onSkip = onSkip,
        skipLabel = "Skip tour",
        step = "1 of 3",
    )
}

@Composable
private fun DaemonPage(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardingPage(
        title = "Pair with the daemon",
        body = "On your desktop, open the Codetalker dashboard's Preferences → AR Companion " +
            "and click Pair AR Companion. A QR code appears with a daemon URL and a one-time " +
            "pairing token. Scan it from the next screen, or paste the URL + token by hand.",
        nextLabel = "Continue",
        onNext = onNext,
        onSkip = onSkip,
        onBack = onBack,
        skipLabel = "Skip tour",
        step = "2 of 3",
    )
}

@Composable
private fun PermissionsPage(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingPage(
        title = "A few permissions",
        body = "Camera — to read the pairing QR. Microphone — for the side-button voice " +
            "input. Notifications — so Codetalker can keep audio playing while the screen is " +
            "off. We'll ask for each on the next screen, with details for why.",
        nextLabel = "Allow permissions",
        onNext = onNext,
        onSkip = null,
        onBack = onBack,
        step = "3 of 3",
    )
}

@Composable
private fun OnboardingPage(
    title: String,
    body: String,
    nextLabel: String,
    onNext: () -> Unit,
    onSkip: (() -> Unit)?,
    skipLabel: String = "Skip",
    onBack: (() -> Unit)? = null,
    step: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(step, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(body, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            if (onBack != null) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.width(12.dp))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.width(200.dp),
            ) { Text(nextLabel) }
            if (onSkip != null) {
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onSkip) { Text(skipLabel) }
            }
        }
    }
}
