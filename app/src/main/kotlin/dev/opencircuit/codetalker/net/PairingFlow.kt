package dev.opencircuit.codetalker.net

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * CCT-31 Phase 5 — daemon pairing token + URL persistence.
 *
 * The dashboard's "Pair AR Companion" button generates a QR encoding:
 *
 *   {"daemon_url": "http://192.168.1.42:17832", "pairing_token": "<32+ chars>"}
 *
 * The Android app scans the QR, parses the JSON, and stores both fields
 * in EncryptedSharedPreferences (Android Keystore-backed). On every
 * launch the app reads them back; if missing, it shows the pairing screen.
 */
class PairingFlow(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "codetalker_pairing",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Returns null if not yet paired. */
    fun current(): Pairing? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return Pairing(daemonUrl = url, pairingToken = token)
    }

    /** Parse a QR payload and store. Throws if payload is malformed. */
    fun savePairing(qrPayload: String): Pairing {
        val o = JSONObject(qrPayload)
        val url = o.getString("daemon_url").trimEnd('/')
        val token = o.getString("pairing_token")
        require(url.isNotEmpty() && token.length >= 16) { "invalid pairing payload" }
        prefs.edit().putString(KEY_URL, url).putString(KEY_TOKEN, token).apply()
        return Pairing(daemonUrl = url, pairingToken = token)
    }

    /** For "manual entry" flow when QR scanning isn't available. */
    fun saveManual(daemonUrl: String, pairingToken: String): Pairing {
        require(daemonUrl.startsWith("http")) { "daemon_url must include http(s)://" }
        require(pairingToken.length >= 16) { "pairing token too short" }
        val cleaned = daemonUrl.trimEnd('/')
        prefs.edit().putString(KEY_URL, cleaned).putString(KEY_TOKEN, pairingToken).apply()
        return Pairing(daemonUrl = cleaned, pairingToken = pairingToken)
    }

    fun clear() {
        prefs.edit().remove(KEY_URL).remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val KEY_URL = "daemon_url"
        private const val KEY_TOKEN = "pairing_token"
    }
}

data class Pairing(
    val daemonUrl: String,
    val pairingToken: String,
)
