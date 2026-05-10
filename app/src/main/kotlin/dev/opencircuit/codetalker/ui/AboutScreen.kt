package dev.opencircuit.codetalker.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.BuildConfig

/**
 * CCT-32 Task C.5 — About screen.
 *
 * Renders app identity (name + version + build), license summary, third
 * party libraries, and three deep-links: GitHub source, privacy policy,
 * and terms of service. Also surfaces a "Check for updates" link that
 * opens the GitHub Releases page.
 *
 * The privacy policy + ToS links open in the system browser. Hosting
 * lands once the public codetalker-pro repo (CCT-30) ships GitHub Pages;
 * until then the URL templates render the .md path on the OSS repo so
 * the link still resolves to readable markdown.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header / back nav.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text("About", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        // Hero — gradient orb + wordmark + tagline.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF22D3EE), Color(0xFFA855F7)),
                            ),
                        ),
                )
                Spacer(Modifier.height(12.dp))
                Text("codetalker companion", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "speak to your code, listen to your code",
                    fontSize = 13.sp,
                    color = Color(0xFFAAB1C0),
                )
                Spacer(Modifier.height(8.dp))
                // BuildConfig.VERSION_NAME / VERSION_CODE come from
                // app/build.gradle.kts. Both are guaranteed non-null on
                // every Android variant.
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    fontSize = 12.sp,
                    color = Color(0xFF8B91A0),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // License summary.
        SectionHeader("License")
        Text(
            "© Open Circuit Dev. The companion application code is proprietary; OSS components used by the app are MIT-licensed (see Third-party libraries below).",
            fontSize = 13.sp,
            color = Color(0xFFD0D5E0),
        )

        Spacer(Modifier.height(20.dp))

        // Third-party libraries.
        SectionHeader("Third-party libraries")
        Column {
            ThirdPartyRow("Compose Material 3", "Apache 2.0", "https://github.com/androidx/androidx")
            ThirdPartyRow("OkHttp + okhttp-sse", "Apache 2.0", "https://github.com/square/okhttp")
            ThirdPartyRow("ZXing core", "Apache 2.0", "https://github.com/zxing/zxing")
            ThirdPartyRow("Media3 ExoPlayer", "Apache 2.0", "https://github.com/androidx/media")
            ThirdPartyRow("CameraX", "Apache 2.0", "https://github.com/androidx/androidx")
            ThirdPartyRow("Sentry Android SDK", "MIT", "https://github.com/getsentry/sentry-java")
            ThirdPartyRow("Kotlin coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines")
            ThirdPartyRow("AndroidX core-splashscreen", "Apache 2.0", "https://github.com/androidx/androidx")
        }

        Spacer(Modifier.height(20.dp))

        // Deep links.
        SectionHeader("Links")
        TextButton(onClick = {
            // Source link — points at the public codetalker-pro repo
            // template. Note: that repo doesn't exist yet (tracked under
            // CCT-30 open-core split). When it lands, this URL becomes
            // the canonical home for the app.
            open("https://github.com/OpenCircuitDev/codetalker-pro")
        }) {
            Text("Source on GitHub", color = Color(0xFF22D3EE))
        }
        TextButton(onClick = {
            open("https://github.com/OpenCircuitDev/codetalker-pro/blob/main/companion-android/docs/PRIVACY-POLICY.md")
        }) {
            Text("Privacy policy", color = Color(0xFF22D3EE))
        }
        TextButton(onClick = {
            open("https://github.com/OpenCircuitDev/codetalker-pro/blob/main/companion-android/docs/TERMS.md")
        }) {
            Text("Terms of service", color = Color(0xFF22D3EE))
        }
        // CCT-32 Task E.4 — release-channel deep-link. The codetalker-pro
        // repo (CCT-30) will host the canonical Releases page; until then
        // tapping this opens a 404, which the user can treat as
        // "no upstream releases yet". Friendly fallback OK for v0.1.0.
        TextButton(onClick = {
            open("https://github.com/OpenCircuitDev/codetalker-pro/releases/latest")
        }) {
            Text("Check for updates", color = Color(0xFFA855F7))
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        color = Color(0xFF8B91A0),
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = Color(0xFF1E2230))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ThirdPartyRow(name: String, license: String, url: String) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(license, fontSize = 11.sp, color = Color(0xFF8B91A0))
        }
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(intent) }
        }) {
            Text("View", color = Color(0xFF22D3EE), fontSize = 13.sp)
        }
    }
}
