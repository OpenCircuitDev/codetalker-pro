package dev.opencircuit.codetalker.hud

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Phase 7 (2026-05-16) — lifecycle owner for the AR HUD on Display 6.
 *
 * Responsibilities:
 *   - Watch DisplayManager for external-display attach / detach.
 *   - On attach: open a [GlassesHud] Presentation rendering captions.
 *   - On detach: dismiss the Presentation.
 *   - On controller close: dismiss + unregister listeners.
 *
 * Created by MainActivity once per pairing. Provided via
 * [LocalGlassesHudController] so Preferences / Diagnostics screens
 * can show HUD state without prop-drilling.
 */
class GlassesHudController(
    private val appContext: Context,
    private val daemonBaseUrl: String,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateOwner: SavedStateRegistryOwner,
    private val compositionContext: CompositionContext,
) {
    companion object {
        private const val TAG = "GlassesHudController"
    }

    private val displayManager =
        appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var hud: GlassesHud? = null
    @Volatile
    private var enabled: Boolean = true

    /** True iff an external display is currently attached. UI can
     *  observe via [LocalGlassesHudController] for status indicators. */
    @Volatile
    var displayAttached: Boolean = false
        private set

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "display added: $displayId")
            evaluate()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "display removed: $displayId")
            evaluate()
        }

        override fun onDisplayChanged(displayId: Int) {
            // ignore — metric / rotation changes don't affect HUD attach state
        }
    }

    /** Begin watching for displays. Idempotent. */
    fun start() {
        displayManager.registerDisplayListener(displayListener, handler)
        // Initial check in case a display was already attached on launch.
        evaluate()
    }

    /** Disable the HUD without stopping the listener (so re-enable is
     *  cheap). Dismisses any open Presentation. */
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        evaluate()
    }

    fun close() {
        try {
            displayManager.unregisterDisplayListener(displayListener)
        } catch (_: Throwable) { /* listener may not have been registered */ }
        dismissHud()
    }

    private fun evaluate() {
        val external = findExternalDisplay(appContext)
        displayAttached = external != null
        if (external != null && enabled) {
            showHud(external)
        } else {
            dismissHud()
        }
    }

    private fun showHud(display: android.view.Display) {
        if (hud != null) return
        try {
            val newHud = GlassesHud(
                outerContext = appContext,
                display = display,
                daemonBaseUrl = daemonBaseUrl,
                lifecycleOwner = lifecycleOwner,
                savedStateOwner = savedStateOwner,
                compositionContext = compositionContext,
            )
            newHud.show()
            hud = newHud
            Log.d(TAG, "HUD shown on display ${display.displayId}")
        } catch (t: Throwable) {
            Log.w(TAG, "showHud failed: ${t.message}")
        }
    }

    private fun dismissHud() {
        val current = hud
        hud = null
        try {
            current?.dismiss()
        } catch (_: Throwable) { /* best-effort */ }
    }
}


/**
 * CompositionLocal holder so Preferences / Diagnostics screens can
 * read HUD state (e.g. show "Glasses connected" badge) without
 * prop-drilling through every screen-level Composable.
 */
val LocalGlassesHudController = compositionLocalOf<GlassesHudController?> { null }
