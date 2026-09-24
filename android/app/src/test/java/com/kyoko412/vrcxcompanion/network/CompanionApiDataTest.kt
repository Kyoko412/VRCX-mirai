package com.kyoko412.vrcxcompanion.network

import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CertificatePinner
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CompanionApiDataTest {
    @Test fun mapsHttpFailuresAndRejectsUnsupportedStatus() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, CertificatePinner.pin(certificate.certificate))
            val api = CompanionApi(endpoint, PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress()))
            for ((code, expected) in listOf(
                400 to ApiFailure.InvalidRequest::class.java,
                401 to ApiFailure.Unauthorized::class.java,
                403 to ApiFailure.Revoked::class.java,
                409 to ApiFailure.SessionChanged::class.java,
                503 to ApiFailure.Unavailable::class.java
            )) {
                server.enqueue(MockResponse.Builder().code(code).body("""{"code":"error"}""").build())
                assertThrows(expected) { runBlocking { api.status("token") } }
            }
            server.enqueue(MockResponse.Builder().code(403)
                .body("""{"code":"account_changed","message":"Desktop account changed"}""").build())
            assertThrows(ApiFailure.AccountChanged::class.java) { runBlocking { api.status("token") } }
            server.enqueue(MockResponse.Builder().body("""{"apiVersion":2,"accountId":"usr_a","computerName":"PC","syncState":"ready"}""").build())
            assertThrows(ApiFailure.IncompatibleVersion::class.java) { runBlocking { api.status("token") } }
            Unit
        }
    }

    @Test fun decodesMultilineNonAsciiBioWithoutHtmlInterpretation() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, CertificatePinner.pin(certificate.certificate))
            val api = CompanionApi(endpoint, PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress()))
            server.enqueue(MockResponse.Builder().body("""{"accountId":"usr_a","items":[{"id":1,"previousBio":"旧简介","bio":"第一行\n<script>第二行</script>","observedAt":"2026-09-24T02:00:00Z"}],"nextCursor":null}""").build())
            val bio = api.bioHistory("token", "usr_friend", null).items.single()
            assertEquals("第一行\n<script>第二行</script>", bio.bio)
            assertEquals("旧简介", bio.previousBio)
        }
    }
}
