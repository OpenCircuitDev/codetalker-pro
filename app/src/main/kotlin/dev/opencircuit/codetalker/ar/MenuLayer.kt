package dev.opencircuit.codetalker.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.net.SessionLite

/**
 * CCT-31 Phase 8 — Menu layer (world-pinned at arm's length).
 *
 * Mirrors the mockup at docs/mockups/index.html#mockup-xreal.hud.menu.
 * Volume rocker scrolls; click confirms; long-press dismisses.
 *
 * The Nebula SDK anchors this 50cm in front of the user with billboarding
 * so it always faces them as they turn their head.
 */
@Composable
fun MenuLayer(
    selectedIndex: Int,
    sessions: List<SessionLite> = emptyList(),
) {
    // TODO(nebula): wrap this Box in NebulaWorldAnchor(distanceMeters = 0.5f, billboard = true)
    //   so the panel is world-pinned but always faces the user.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE612121A)) // heavier glass for menu
                .border(1.dp, Color(0x66FBBF24), RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            // Header
            Text(
                text = "↑ rocker · click to confirm",
                color = Color(0xFFFBBF24),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Session list
            if (sessions.isEmpty()) {
                Text(
                    "No sessions to choose.",
                    color = Color(0xFF8A8F9C),
                    fontSize = 12.sp,
                )
            } else {
                sessions.forEachIndexed { idx, s ->
                    val isSel = idx == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSel) Color(0x33FBBF24) else Color.Transparent,
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Live dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (s.isLive) Color(0xFF34D399) else Color(0xFF52525B),
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                s.displayName,
                                color = if (isSel) Color(0xFFE6E8EE) else Color(0xFFAAB1C0),
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            )
                            s.attachedCharacter?.let { c ->
                                Text(
                                    "${c.displayName} · ${c.persona ?: "no persona"}",
                                    color = Color(0xFF6E7585),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                        if (isSel) {
                            Text("▶", color = Color(0xFFFBBF24), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Footer
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF1E1E2E)),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("long-press to exit", color = Color(0xFF6E7585), fontSize = 9.sp)
                Text("${sessions.size} sessions", color = Color(0xFF6E7585), fontSize = 9.sp)
            }
        }
    }
}
