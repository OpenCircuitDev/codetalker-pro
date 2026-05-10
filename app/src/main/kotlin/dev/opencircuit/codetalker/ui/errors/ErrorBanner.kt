package dev.opencircuit.codetalker.ui.errors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CCT-32 Task B.3 — error banner for recoverable failures.
 *
 * Renders an [AppError] with an optional Retry / Open Settings / Re-pair
 * action and a Dismiss button. Designed to be stacked at the top of any
 * screen via:
 *
 *   error?.let {
 *       ErrorBanner(it, onAction = { ... }, onDismiss = { error = null })
 *   }
 *
 * Why a sealed banner: the Beam Pro screen is dim, so a full-bleed Snackbar
 * blends in. A persistent banner with a clear action button is easier to
 * find while wearing glasses.
 */
@Composable
fun ErrorBanner(
    error: AppError,
    onAction: (AppError) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF7A1F2E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            error.title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            error.body,
            color = Color(0xFFFFE0E5),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            error.actionLabel?.let { label ->
                Button(
                    onClick = { onAction(error) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF7A1F2E),
                    ),
                ) { Text(label, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = Color(0xFFFFE0E5))
            }
        }
    }
}
