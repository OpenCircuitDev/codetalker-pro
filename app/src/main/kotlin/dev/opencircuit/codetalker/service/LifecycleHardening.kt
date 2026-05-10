package dev.opencircuit.codetalker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * CCT-32 Task B.5 — pause/resume audio on screen-off, reconnect SSE on
 * network change.
 *
 * The activity owns one [ScreenStateObserver] that flips a StateFlow
 * mirroring screen-on / screen-off, plus one [NetworkStateObserver] that
 * flips when ConnectivityManager surfaces an onAvailable / onLost. The
 * foreground service + companion view model subscribe to the flows.
 *
 * Pure logic (no Compose / no Activity) lives here so unit tests can
 * drive the flows directly without instrumenting the platform.
 */
class ScreenStateObserver : BroadcastReceiver(), DefaultLifecycleObserver {

    private val _screenOn = MutableStateFlow(true)
    val screenOn: StateFlow<Boolean> = _screenOn

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(this, filter)
        }
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (_: Throwable) { /* idempotent */ }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> _screenOn.value = true
            Intent.ACTION_SCREEN_OFF -> _screenOn.value = false
        }
    }

    /** Allow tests + lifecycle owners to drive directly. */
    fun setScreenOn(value: Boolean) { _screenOn.value = value }
}

class NetworkStateObserver(
    private val cm: ConnectivityManager,
) {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online

    /**
     * Fires whenever a usable network appears (after a drop). Subscribers
     * should kick their SSE / pending HTTP work onto a fresh attempt.
     */
    private val _reconnectTick = MutableStateFlow(0)
    val reconnectTick: StateFlow<Int> = _reconnectTick

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = true
            _reconnectTick.value = _reconnectTick.value + 1
        }
        override fun onLost(network: Network) {
            _online.value = false
        }
    }

    fun register() {
        val req = NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, callback)
    }

    fun unregister() {
        try { cm.unregisterNetworkCallback(callback) } catch (_: Throwable) {}
    }

    /** Test hook so unit tests can drive transitions without a real CM. */
    fun setOnline(value: Boolean) {
        if (_online.value != value) {
            _online.value = value
            if (value) _reconnectTick.value = _reconnectTick.value + 1
        }
    }
}
