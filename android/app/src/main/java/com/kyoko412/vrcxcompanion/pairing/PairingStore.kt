package com.kyoko412.vrcxcompanion.pairing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kyoko412.vrcxcompanion.network.PinnedEndpoint
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedPairing(
    val address: String,
    val port: Int,
    val spkiSha256: String,
    val deviceId: String,
    val token: String
) {
    override fun toString(): String =
        "SavedPairing(address=$address, port=$port, deviceId=$deviceId, token=<redacted>)"
    fun endpoint() = PinnedEndpoint(address, port, spkiSha256)
}

interface PairingPreferences {
    fun read(key: String): String?
    fun write(values: Map<String, String>)
    fun clear()
}

interface TokenCipher {
    fun encrypt(token: String): String
    fun decrypt(ciphertext: String): String
}

class PairingStore(private val preferences: PairingPreferences, private val cipher: TokenCipher) {
    fun save(pairing: SavedPairing) {
        pairing.endpoint()
        require(pairing.deviceId.isNotBlank() && pairing.token.isNotBlank())
        preferences.write(mapOf(
            "address" to pairing.address,
            "port" to pairing.port.toString(),
            "pin" to pairing.spkiSha256,
            "deviceId" to pairing.deviceId,
            "encryptedToken" to cipher.encrypt(pairing.token)
        ))
    }

    fun load(): SavedPairing? {
        if (preferences.read("encryptedToken") == null) return null
        return try {
            SavedPairing(
                preferences.read("address") ?: error("Missing address"),
                preferences.read("port")?.toInt() ?: error("Missing port"),
                preferences.read("pin") ?: error("Missing pin"),
                preferences.read("deviceId") ?: error("Missing device ID"),
                cipher.decrypt(preferences.read("encryptedToken") ?: error("Missing token"))
            ).also { it.endpoint() }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() = preferences.clear()

    companion object {
        fun forAndroid(context: Context): PairingStore =
            PairingStore(AndroidPairingPreferences(context.applicationContext), AndroidKeystoreCipher())
    }
}

private class AndroidPairingPreferences(context: Context) : PairingPreferences {
    private val prefs = context.getSharedPreferences("mobile_companion_pairing_v1", Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(values: Map<String, String>) {
        val edit = prefs.edit().clear()
        values.forEach { (key, value) -> edit.putString(key, value) }
        check(edit.commit()) { "Could not save pairing" }
    }
    override fun clear() { check(prefs.edit().clear().commit()) }
}

private class AndroidKeystoreCipher : TokenCipher {
    private val alias = "vrcx_mirai_companion_token_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    override fun encrypt(token: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(data)
    }

    override fun decrypt(ciphertext: String): String {
        val data = Base64.getDecoder().decode(ciphertext)
        require(data.size > 12 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
    }
}
