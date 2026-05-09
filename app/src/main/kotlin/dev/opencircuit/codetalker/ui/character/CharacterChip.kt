package dev.opencircuit.codetalker.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.net.AttachedCharacter

/**
 * CCT-31 character UI port — mirrors the React Phase 27 PersonaBadge +
 * CharacterAvatar so the AR companion's "who's speaking" surface looks
 * coherent with the desktop dashboard.
 *
 * The persona color mapping is the same one shipped in the dashboard
 * (PersonaBadge.tsx). Voice cloning is implicit: the listener hears the
 * character's voice via the daemon's TTS stream; this chip just labels
 * what they're hearing.
 */

private data class PersonaPalette(
    val gradientStart: Color,
    val gradientEnd: Color,
    val badgeBg: Color,
    val badgeFg: Color,
)

private val PERSONA_PALETTES: Map<String, PersonaPalette> = mapOf(
    // Match Phase 27 React tokens — slate / amber / cyan / zinc / fuchsia / rose.
    "methodical" to PersonaPalette(Color(0xFF334155), Color(0xFF0F172A), Color(0xFF334155), Color(0xFFE2E8F0)),
    "warm"        to PersonaPalette(Color(0xFFB45309), Color(0xFF7C2D12), Color(0xFFB45309), Color(0xFFFEF3C7)),
    "technical"   to PersonaPalette(Color(0xFF0E7490), Color(0xFF164E63), Color(0xFF0E7490), Color(0xFFCFFAFE)),
    "plain"       to PersonaPalette(Color(0xFF52525B), Color(0xFF27272A), Color(0xFF52525B), Color(0xFFE4E4E7)),
    "sarcastic"   to PersonaPalette(Color(0xFFA21CAF), Color(0xFF581C87), Color(0xFFA21CAF), Color(0xFFFAE8FF)),
    "energetic"   to PersonaPalette(Color(0xFFE11D48), Color(0xFFC2410C), Color(0xFFE11D48), Color(0xFFFEE2E2)),
)

private val NEUTRAL = PersonaPalette(
    Color(0xFF52525B), Color(0xFF27272A), Color(0xFF3F3F46), Color(0xFFA1A1AA)
)

/**
 * Compact "who's speaking" chip suitable for inline use next to a
 * session's display name or in the HUD. Renders an avatar circle + name +
 * persona badge.
 */
@Composable
fun CharacterChip(
    character: AttachedCharacter,
    modifier: Modifier = Modifier,
) {
    val palette = PERSONA_PALETTES[character.persona] ?: NEUTRAL
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CharacterAvatar(character = character, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                character.displayName,
                color = Color(0xFFE6E8EE),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            character.persona?.let {
                Spacer(Modifier.size(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.badgeBg)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        it,
                        color = palette.badgeFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Circular avatar with a persona-themed gradient. v1 shows the character's
 * first letter; Phase 8 (AR HUD) will swap this for a low-poly mesh
 * silhouette streamed from the daemon when available.
 */
@Composable
fun CharacterAvatar(
    character: AttachedCharacter,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val palette = PERSONA_PALETTES[character.persona] ?: NEUTRAL
    val initial = character.displayName.firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(palette.gradientStart, palette.gradientEnd),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            color = Color.White,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
