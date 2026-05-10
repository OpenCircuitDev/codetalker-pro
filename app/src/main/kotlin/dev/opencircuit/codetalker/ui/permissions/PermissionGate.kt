package dev.opencircuit.codetalker.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * CCT-32 Task B.1 — pre-permission rationale + recovery screen.
 *
 * Shows the user *why* we need a permission BEFORE the system dialog,
 * then handles the three outcomes:
 *   1. Granted -> [onAllGranted]
 *   2. Denied (still askable) -> show rationale with "Try again" button
 *   3. Denied permanently (Don't Ask Again) -> show "Open Settings" recovery
 *
 * The composable walks [permissions] one at a time so the user gets a
 * contextual rationale per permission rather than a confusing batch ask.
 */
@Composable
fun PermissionGate(
    permissions: List<PermissionInfo>,
    onAllGranted: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var index by remember { mutableIntStateOf(0) }
    // tracked to detect "permanently denied" via shouldShowRequestPermissionRationale.
    var lastDenied by remember { mutableStateOf<PermissionInfo?>(null) }

    val advance = rememberUpdatedState {
        index += 1
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val current = permissions.getOrNull(index)
        if (current == null) return@rememberLauncherForActivityResult
        if (granted) {
            advance.value()
            lastDenied = null
        } else {
            // If the OS would no longer show a rationale dialog, the user
            // chose Don't Ask Again — flip into recovery mode.
            lastDenied = current
        }
    }

    // Once we've walked past the last permission, signal completion.
    LaunchedEffect(index, permissions.size) {
        if (index >= permissions.size) onAllGranted()
    }

    val current = permissions.getOrNull(index) ?: return
    val granted = ContextCompat.checkSelfPermission(context, current.manifest) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) {
        // Already granted — advance immediately.
        LaunchedEffect(current.manifest) { advance.value() }
        return
    }

    val permanentlyDenied = lastDenied?.manifest == current.manifest &&
        activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, current.manifest)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Allow ${current.displayName}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Step ${index + 1} of ${permissions.size}", fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Text(current.rationale, fontSize = 14.sp)

        if (permanentlyDenied) {
            Spacer(Modifier.height(20.dp))
            Text(current.settingsHint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            Row {
                Button(
                    onClick = { openAppSettings(context) },
                    modifier = Modifier.width(180.dp),
                ) { Text("Open Settings") }
                if (onSkip != null) {
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onSkip) { Text("Skip for now") }
                }
            }
        } else {
            Spacer(Modifier.height(20.dp))
            Row {
                Button(
                    onClick = { launcher.launch(current.manifest) },
                    modifier = Modifier.width(180.dp),
                ) { Text(if (lastDenied?.manifest == current.manifest) "Try again" else "Allow") }
                if (onSkip != null) {
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onSkip) { Text("Skip for now") }
                }
            }
        }
    }
}

/**
 * Launches the system's per-app settings page so the user can flip the
 * permission switch by hand. Fires a new task because we may be running
 * inside a non-Activity context (e.g. notification trampoline).
 */
internal fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is Activity) return ctx
        ctx = (ctx as? android.content.ContextWrapper)?.baseContext
    }
    return null
}
