package dev.opencircuit.codetalker.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.opencircuit.codetalker.net.DaemonClient

/**
 * CCT-31 Phase 7 — TTS audio playback for the AR companion.
 *
 * Subscribes to the daemon's per-session audio stream
 * (`/api/companion/audio-stream/{session_id}`) and pipes it through
 * ExoPlayer to the phone's speakers (or to the glasses' built-in
 * speakers when XREAL Air 2 Pro is connected — Android routes audio
 * to the most-recently-connected output by default).
 *
 * v1 daemon emits raw WAV bytes (per the Phase 5 implementer's
 * pragmatic deferral of Opus encoding); ExoPlayer decodes WAV natively.
 * When the daemon adds Opus encoding (CCT-31 paid Phase 7 follow-up),
 * this wrapper changes only the Content-Type expectation.
 *
 * The X-CCT-Pairing-Token header is added via a custom DataSource.Factory
 * so the daemon's auth layer accepts the request.
 */
@UnstableApi
class TTSPlayer(
    context: Context,
    private val daemonClient: DaemonClient,
    private val pairingToken: String,
    private val audioFocusManager: AudioFocusManager = AudioFocusManager.forContext(context),
) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    /**
     * Subscribe to a session's audio stream and start playing.
     *
     * CCT-32 Task A.7: requests audio focus before play; pauses on
     * transient focus loss (incoming call, navigation prompt) and
     * resumes on focus gain. Permanent loss stops playback.
     */
    fun playSession(sessionId: String) {
        val granted = audioFocusManager.requestFocus(
            onPause = { exoPlayer.playWhenReady = false },
            onResume = { exoPlayer.playWhenReady = true },
            onStop = {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            },
        )
        if (!granted) {
            // No focus — bail rather than play over another app.
            return
        }
        val url = daemonClient.audioStreamUrl(sessionId)
        val mediaItem = MediaItem.fromUri(url)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf(DaemonClient.HEADER_TOKEN to pairingToken)
            )
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = ProgressiveMediaSource.Factory(httpFactory)
            .createMediaSource(mediaItem)

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun pause() {
        exoPlayer.playWhenReady = false
    }

    fun resume() {
        exoPlayer.playWhenReady = true
    }

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        audioFocusManager.releaseFocus()
    }

    fun release() {
        exoPlayer.release()
        audioFocusManager.releaseFocus()
    }

    /** True while audio is actively rendering. */
    fun isPlaying(): Boolean = exoPlayer.playbackState == Player.STATE_READY && exoPlayer.playWhenReady
}
