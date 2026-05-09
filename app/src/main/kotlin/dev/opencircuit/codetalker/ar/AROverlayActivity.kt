package dev.opencircuit.codetalker.ar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.opencircuit.codetalker.input.ButtonRouter
import dev.opencircuit.codetalker.input.ButtonState

/**
 * CCT-31 Phase 8 — AR composition root.
 *
 * Three layers compose into the XREAL Air 2 Pro display:
 *   1. [HudLayer] — head-pinned chip (always visible, ~6° below gaze)
 *   2. [MenuLayer] — world-pinned session selector (visible during MENU state)
 *   3. [MirrorLayer] — world-pinned screen mirror (visible when toggled)
 *
 * Each layer is a Composable so we get React-style state-driven rendering
 * without re-implementing layout. Nebula SDK provides the spatial anchoring
 * (head-pinned vs world-pinned); we just hand it Compose surfaces and
 * declare the anchor type.
 *
 * Status: SKELETON. Compose UI for the three layers is here and renders on
 * any Android phone (looks like a flat overlay). The actual AR depth +
 * world-pinning happens via Nebula SDK calls marked `TODO(nebula)` below.
 * When the AAR is dropped into app/libs/, swap those TODO markers for the
 * real SDK invocations — typically 5-10 lines per layer.
 *
 * AndroidManifest registration: this Activity is launched in addition to
 * [MainActivity] when the user is wearing glasses. The transition is
 * driven by the ConfigurationManager once the Nebula SDK reports
 * "glasses connected."
 */
class AROverlayActivity : ComponentActivity() {

    private val buttonRouter = ButtonRouter()
    // TODO(nebula): inject NebulaSession via dependency provider once the
    // SDK is on the classpath; for now the skeleton holds null and the
    // layers degrade to non-AR Compose overlays for emulator testing.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO(nebula): NebulaSession.create(this).start()
        //   This brings up the AR composition root and asks the OS to
        //   route DisplayPort over USB-C to the connected XREAL glasses.

        setContent {
            ARCompositionRoot(buttonRouter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO(nebula): NebulaSession.stop()
    }
}

@Composable
private fun ARCompositionRoot(buttonRouter: ButtonRouter) {
    val state by buttonRouter.state.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
    ) {
        // Layer 1: HUD always visible
        HudLayer(state = state)

        // Layer 2: Menu when MENU state
        if (state is ButtonState.Menu) {
            MenuLayer(selectedIndex = (state as ButtonState.Menu).selectedIndex)
        }

        // Layer 3: Mirror when toggled — managed by ConfigurationManager
        // (CCT-31 Phase 9-AR follow-up). Stub for now.
    }
}
