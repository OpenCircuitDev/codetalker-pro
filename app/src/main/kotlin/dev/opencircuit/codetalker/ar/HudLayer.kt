package dev.opencircuit.codetalker.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import dev.opencircuit.codetalker.input.ButtonState
import dev.opencircuit.codetalker.net.AttachedCharacter
import dev.opencircuit.codetalker.ui.character.CharacterAvatar

/**
 * CCT-31 Phase 8 — HUD layer (head-pinned, always visible).
 *
 * Mirrors the mockup at docs/mockups/index.html#mockup-xreal.hud.idle.
 * Chip lives ~6° below gaze; Nebula SDK pins it to the user's head pose.
 *
 * State-driven rendering:
 *   - IDLE: chip with character avatar + name + persona + live dot
 *   - LISTENING: same chip + "recording" badge + waveform indicator
 *   - DispatchListening: same as LISTENING but with "sending" caption
 *   - Menu: chip remains visible (the menu is a separate world-pinned layer)
 */
@Composable
fun HudLayer(
    state: ButtonState,
    character: AttachedCharacter? = null,
    isAudioActive: Boolean = true,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // TODO(nebula): wrap this Box in NebulaHeadAnchor(offsetDegrees = -6f)
        //   so it follows the user's head with a 6° downward offset.
        //   For non-AR fallback we use BottomCenter alignment — visually
        //   close enough for emulator dev.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC12121A)) // glass-panel approximation
                .border(1.dp, Color(0x661E1E2E), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (character != null) {
                    CharacterAvatar(character = character, size = 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        character.displayName,
                        color = Color(0xFFE6E8EE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (isAudioActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF34D399)),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = when (state) {
                        is ButtonState.Listening -> "listening"
                        is ButtonState.DispatchListening -> "sending"
                        is ButtonState.Menu -> "menu open"
                        else -> if (isAudioActive) "listening" else "muted"
                    },
                    color = when (state) {
                        is ButtonState.Listening -> Color(0xFF34D399)
                        is ButtonState.DispatchListening -> Color(0xFFFBBF24)
                        else -> Color(0xFFAAB1C0)
                    },
                    fontSize = 10.sp,
                )
            }
        }
    }
}
