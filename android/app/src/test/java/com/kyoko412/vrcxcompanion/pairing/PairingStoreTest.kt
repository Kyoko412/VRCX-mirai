package com.kyoko412.vrcxcompanion.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PairingStoreTest {
    private class MemoryPreferences : PairingPreferences {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(values: Map<String, String>) { this.values.clear(); this.values.putAll(values) }
        override fun clear() { values.clear() }
    }
    private class TestCipher : TokenCipher {
        override fun encrypt(token: String) = token.reversed()
        override fun decrypt(ciphertext: String) = ciphertext.reversed()
    }

    @Test fun onlyEncryptedTokenIsPersisted() {
        val preferences = MemoryPreferences()
        val store = PairingStore(preferences, TestCipher())
        val token = "sensitive-device-token"
        val pairing = SavedPairing("192.168.1.10", 34682, "sha256/" + "A".repeat(43) + "=", "device-1", token)
        store.save(pairing)
        assertFalse(preferences.values.values.any { it.contains(token) })
        assertEquals(pairing, store.load())
        store.clear()
        assertNull(store.load())
    }
}
