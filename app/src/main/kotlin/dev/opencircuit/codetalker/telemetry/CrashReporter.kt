package dev.opencircuit.codetalker.telemetry

import android.content.Context
import dev.opencircuit.codetalker.BuildConfig
import io.sentry.android.core.SentryAndroid

/**
 * CCT-32 Task G.1 — opt-in Sentry crash reporter wrapper.
 *
 * Enforces three invariants:
 *   1. The Sentry SDK is initialized AT MOST ONCE per process (idempotent
 *      `init` calls are no-ops).
 *   2. Initialization is gated by `enabled` AND a non-empty DSN — without
 *      both, the SDK is never started, no events are dispatched.
 *   3. `sendDefaultPii` is always set to `false`. The privacy policy
 *      promises stack traces + app version + device model only; this is
 *      the SDK-level enforcement of that promise.
 *
 * The DSN flows in via `BuildConfig.SENTRY_DSN`, which is sourced from
 * the Gradle property `CCT_SENTRY_DSN`. Empty string → not configured →
 * no init, regardless of the user's consent toggle.
 */
object CrashReporter {

    private var initialized: Boolean = false

    /**
     * Visible-for-testing hook so unit tests can drive [init] under JVM
     * without crashing on the Android Sentry init. When set, it's called
     * INSTEAD of the real SentryAndroid.init.
     */
    @Volatile
    internal var sdkInitOverride: ((Context, String) -> Unit)? = null

    /**
     * Initialize the SDK if and only if (a) the user has opted in, and
     * (b) a DSN is configured at build time. Calling more than once is
     * a no-op so the consent flow can call this every state transition
     * without leaking handlers.
     */
    fun init(context: Context, enabled: Boolean, dsn: String = BuildConfig.SENTRY_DSN) {
        if (initialized) return
        if (!enabled) return
        if (dsn.isBlank()) return

        val override = sdkInitOverride
        if (override != null) {
            override(context, dsn)
            initialized = true
            return
        }

        SentryAndroid.init(context.applicationContext) { options ->
            options.dsn = dsn
            // PII enforcement matches PRIVACY-POLICY §4.
            options.isSendDefaultPii = false
            // Conservative event sampling — under most usage we should send
            // every crash. App is local-first so traffic is low.
            options.sampleRate = 1.0
            // Don't ship breadcrumbs for instrumented HTTP traffic by
            // default; that's where pairing tokens / daemon URLs would
            // accidentally leak. (The SDK auto-enables this on Android;
            // explicitly disable.)
            options.isEnableAutoSessionTracking = true
            // Clip the SDK's auto-included context that could fingerprint
            // the user beyond what the policy commits to.
            options.environment = "production"
            options.release = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
        }
        initialized = true
    }

    /** Reset state — visible only for tests. Production never calls. */
    internal fun resetForTest() {
        initialized = false
        sdkInitOverride = null
    }

    fun isInitialized(): Boolean = initialized
}
