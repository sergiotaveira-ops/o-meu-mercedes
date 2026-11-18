package pt.sergiotaveira.mercedes.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/** Tokens and Mercedes credentials, encrypted with a key held in the Android Keystore. */
class EncryptedTokenStore(context: Context) : TokenStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mercedes_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun load(): Tokens? {
        val a = prefs.getString("access", null) ?: return null
        val r = prefs.getString("refresh", null) ?: return null
        return Tokens(a, r, prefs.getLong("expires", 0))
    }

    override fun save(t: Tokens) {
        prefs.edit().putString("access", t.accessToken).putString("refresh", t.refreshToken).putLong("expires", t.expiresAtEpochSec).apply()
    }

    override fun clear() {
        prefs.edit().remove("access").remove("refresh").remove("expires").remove("email").remove("password").apply()
    }

    override fun saveCredentials(email: String, password: String) {
        prefs.edit().putString("email", email).putString("password", password).apply()
    }

    override fun credentials(): Pair<String, String>? {
        val e = prefs.getString("email", null) ?: return null
        val p = prefs.getString("password", null) ?: return null
        return e to p
    }

    override fun deviceId(): String =
        prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }
}
