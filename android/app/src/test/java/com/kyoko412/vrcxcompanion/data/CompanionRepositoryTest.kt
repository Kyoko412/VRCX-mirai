package com.kyoko412.vrcxcompanion.data

import com.kyoko412.vrcxcompanion.network.ApiFailure
import com.kyoko412.vrcxcompanion.network.BioChangeDto
import com.kyoko412.vrcxcompanion.network.CompanionApi
import com.kyoko412.vrcxcompanion.network.Page
import com.kyoko412.vrcxcompanion.network.PinnedClientFactory
import com.kyoko412.vrcxcompanion.network.PinnedEndpoint
import com.kyoko412.vrcxcompanion.pairing.PairingPreferences
import com.kyoko412.vrcxcompanion.pairing.PairingStore
import com.kyoko412.vrcxcompanion.pairing.SavedPairing
import com.kyoko412.vrcxcompanion.pairing.TokenCipher
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CertificatePinner
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRepositoryTest {
    private class MemoryPreferences : PairingPreferences {
        private val map = mutableMapOf<String, String>()
        override fun read(key: String) = map[key]
        override fun write(values: Map<String, String>) { map.clear(); map.putAll(values) }
        override fun clear() { map.clear() }
    }
    private class TestCipher : TokenCipher {
        override fun encrypt(token: String) = token.reversed()
        override fun decrypt(ciphertext: String) = ciphertext.reversed()
    }

    @Test fun tiedTimestampPagesPreserveEveryStableIdOnce() {
        val accumulator = PageAccumulator<BioChangeDto> { it.id.toString() }
        val timestamp = "2026-09-24T02:00:00Z"
        val first = Page("usr_a", (2L..51L).reversed().map { BioChangeDto(it, "old", "new", timestamp) }, "opaque")
        val second = Page("usr_a", listOf(BioChangeDto(1, "old", "new", timestamp)), null)
        accumulator.accept(first, "usr_a")
        accumulator.accept(second, "usr_a")
        assertEquals(51, accumulator.items.map { it.id }.distinct().size)
        assertNull(accumulator.nextCursor)
    }

    @Test fun accountChangeClearsPagesAndKeepsTokenForReconfirmation() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, CertificatePinner.pin(certificate.certificate))
            val store = PairingStore(MemoryPreferences(), TestCipher())
            store.save(SavedPairing(endpoint.address, endpoint.port, endpoint.spkiSha256, "dev", "token"))
            val api = CompanionApi(endpoint, PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress()))
            val repo = CompanionRepository(store, api)
            server.enqueue(MockResponse.Builder().body("""{"apiVersion":1,"accountId":"usr_a","computerName":"PC","syncState":"ready"}""").build())
            server.enqueue(MockResponse.Builder().body("""{"accountId":"usr_a","items":[{"id":"usr_x","displayName":"小明"}],"nextCursor":"same/time"}""").build())
            assertEquals("小明", repo.friends(null, null).items.single().displayName)
            assertEquals(1, repo.friendItems(null).size)
            server.enqueue(MockResponse.Builder().body("""{"accountId":"usr_b","items":[],"nextCursor":null}""").build())
            assertThrows(ApiFailure.AccountChanged::class.java) {
                runBlocking { repo.friends(null, "same/time") }
            }
            assertTrue(repo.friendItems(null).isEmpty())
            assertEquals("token", store.load()?.token)
            val secondRequest = server.takeRequest() // status
            val thirdRequest = server.takeRequest() // first friends page
            val fourthRequest = server.takeRequest() // second friends page
            assertEquals("/v1/status", secondRequest.target)
            assertTrue(thirdRequest.target.startsWith("/v1/friends?"))
            assertTrue(fourthRequest.target.contains("cursor=same%2Ftime"))
            server.enqueue(MockResponse.Builder().body("""{"apiVersion":1,"accountId":"usr_b","computerName":"PC","syncState":"ready"}""").build())
            assertThrows(ApiFailure.AccountChanged::class.java) { runBlocking { repo.status() } }
            assertEquals("usr_b", repo.accountId)
        }
    }

    @Test fun revokedTokenClearsPairingAndPages() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, CertificatePinner.pin(certificate.certificate))
            val store = PairingStore(MemoryPreferences(), TestCipher())
            store.save(SavedPairing(endpoint.address, endpoint.port, endpoint.spkiSha256, "dev", "token"))
            val api = CompanionApi(endpoint, PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress()))
            val repo = CompanionRepository(store, api)
            server.enqueue(MockResponse.Builder().code(403).body("""{"code":"forbidden","message":"Device access denied"}""").build())
            assertThrows(ApiFailure.Revoked::class.java) { runBlocking { repo.status() } }
            assertNull(store.load())
        }
    }

    @Test fun desktopWrongAccountResponseKeepsCredentialForReturnToOriginalAccount() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, CertificatePinner.pin(certificate.certificate))
            val store = PairingStore(MemoryPreferences(), TestCipher())
            store.save(SavedPairing(endpoint.address, endpoint.port, endpoint.spkiSha256, "dev", "token"))
            val api = CompanionApi(endpoint, PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress()))
            val repo = CompanionRepository(store, api)
            server.enqueue(MockResponse.Builder().code(403)
                .body("""{"code":"account_changed","message":"Desktop account changed"}""").build())
            assertThrows(ApiFailure.AccountChanged::class.java) { runBlocking { repo.status() } }
            assertEquals("token", store.load()?.token)
        }
    }

    @Test fun rescanUpdatesOnlyMatchingKeyAndRetainsDeviceToken() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val otherCertificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val pin = CertificatePinner.pin(certificate.certificate)
        val endpoint = PinnedEndpoint("192.168.1.10", 34682, pin)
        val store = PairingStore(MemoryPreferences(), TestCipher())
        store.save(SavedPairing(endpoint.address, endpoint.port, pin, "dev", "token"))
        val api = CompanionApi(endpoint, PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress()))
        val repo = CompanionRepository(store, api)
        assertThrows(ApiFailure.TlsMismatch::class.java) {
            repo.updateAddress("192.168.1.11", 34682, CertificatePinner.pin(otherCertificate.certificate))
        }
        assertEquals("192.168.1.10", store.load()?.address)
        repo.updateAddress("192.168.1.11", 34682, pin)
        assertEquals("192.168.1.11", store.load()?.address)
        assertEquals("token", store.load()?.token)
    }

    @Test fun twoBioPagesWithTiedTimestampsRemainComplete() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, CertificatePinner.pin(certificate.certificate))
            val store = PairingStore(MemoryPreferences(), TestCipher())
            store.save(SavedPairing(endpoint.address, endpoint.port, endpoint.spkiSha256, "dev", "token"))
            val api = CompanionApi(endpoint, PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress()))
            val repo = CompanionRepository(store, api)
            val timestamp = "2026-09-24T02:00:00Z"
            val rows = (2..51).reversed().joinToString(",") { id ->
                """{"id":$id,"previousBio":"old","bio":"new","observedAt":"$timestamp"}"""
            }
            server.enqueue(MockResponse.Builder().body("""{"apiVersion":1,"accountId":"usr_a","computerName":"PC","syncState":"ready"}""").build())
            server.enqueue(MockResponse.Builder().body("""{"accountId":"usr_a","items":[$rows],"nextCursor":"opaque"}""").build())
            server.enqueue(MockResponse.Builder().body("""{"accountId":"usr_a","items":[{"id":1,"previousBio":"old","bio":"new","observedAt":"$timestamp"}],"nextCursor":null}""").build())
            val first = repo.bioHistory("usr_friend", null)
            repo.bioHistory("usr_friend", first.nextCursor)
            assertEquals(51, repo.bioItems("usr_friend").map { it.id }.distinct().size)
        }
    }
}
