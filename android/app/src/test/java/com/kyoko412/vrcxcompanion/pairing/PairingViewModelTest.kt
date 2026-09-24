package com.kyoko412.vrcxcompanion.pairing

import com.kyoko412.vrcxcompanion.network.PairingHttpException
import com.kyoko412.vrcxcompanion.network.PairRequestReceipt
import com.kyoko412.vrcxcompanion.network.PairRedeemResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    private val pin = "sha256/" + Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 2 })
    private val offer = PairingQr(1, "192.168.1.10", 34682, "vrcx-companion.invalid", pin, secret)

    private class MemoryPreferences : PairingPreferences {
        private val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(values: Map<String, String>) { this.values.clear(); this.values.putAll(values) }
        override fun clear() { values.clear() }
    }
    private class TestCipher : TokenCipher {
        override fun encrypt(token: String) = token.reversed()
        override fun decrypt(ciphertext: String) = ciphertext.reversed()
    }

    @Test fun waitsForDesktopApprovalAndSavesTokenOnlyAfterRedemption() = runTest {
        val store = PairingStore(MemoryPreferences(), TestCipher())
        var polls = 0
        val transport = object : PairingTransport {
            override suspend fun request(deviceName: String) = PairRequestReceipt("request", "poll-secret")
            override suspend fun redeem(receipt: PairRequestReceipt): PairRedeemResult {
                polls++
                return if (polls == 1) PairRedeemResult("pending")
                else PairRedeemResult(deviceId = "device-1", token = "token-1")
            }
        }
        val model = PairingViewModel({ transport }, store, scope = this, pollIntervalMs = 1, nowMillis = { 0 })
        model.request(offer, "Phone")
        advanceUntilIdle()
        assertEquals(PairingUiState.Approved("device-1"), model.state.value)
        assertEquals("token-1", store.load()?.token)
        assertEquals(2, polls)
    }

    @Test fun rejectionNeverSavesToken() = runTest {
        val store = PairingStore(MemoryPreferences(), TestCipher())
        val transport = object : PairingTransport {
            override suspend fun request(deviceName: String) = PairRequestReceipt("request", "poll-secret")
            override suspend fun redeem(receipt: PairRequestReceipt): PairRedeemResult =
                throw PairingHttpException("pairing_rejected")
        }
        val model = PairingViewModel({ transport }, store, scope = this, pollIntervalMs = 1, nowMillis = { 0 })
        model.request(offer, "Phone")
        advanceUntilIdle()
        assertEquals(PairingUiState.Rejected, model.state.value)
        assertNull(store.load())
    }

    @Test fun expiredOfferStopsBeforeRedeemAndDoesNotPersist() = runTest {
        val store = PairingStore(MemoryPreferences(), TestCipher())
        var redeemCalls = 0
        val transport = object : PairingTransport {
            override suspend fun request(deviceName: String) = PairRequestReceipt("request", "poll-secret")
            override suspend fun redeem(receipt: PairRequestReceipt): PairRedeemResult {
                redeemCalls++
                return PairRedeemResult("pending")
            }
        }
        var clockCalls = 0
        val model = PairingViewModel({ transport }, store, scope = this, pollIntervalMs = 1,
            nowMillis = { if (clockCalls++ == 0) 0 else 120_000 })
        model.request(offer, "Phone")
        advanceUntilIdle()
        assertEquals(PairingUiState.Expired, model.state.value)
        assertEquals(0, redeemCalls)
        assertNull(store.load())
    }
}
