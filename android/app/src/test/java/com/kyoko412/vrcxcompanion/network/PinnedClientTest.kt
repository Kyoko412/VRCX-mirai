package com.kyoko412.vrcxcompanion.network

import com.kyoko412.vrcxcompanion.pairing.PairingQr
import java.net.InetAddress
import javax.net.ssl.SSLException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PinnedClientTest {
    private val host = "vrcx-companion.invalid"

    @Test fun acceptsPinnedCertificateAndRejectsChangedKey() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(host).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            server.enqueue(MockResponse.Builder().body("ok").build())
            val pin = okhttp3.CertificatePinner.pin(certificate.certificate)
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, pin)
            val client = PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress())
            client.newCall(Request.Builder().url("https://$host:${server.port}/v1/status").build())
                .execute().use { assertEquals("ok", it.body.string()) }

            server.enqueue(MockResponse.Builder().body("moved").build())
            val moved = PinnedClientFactory.createForTest(
                endpoint.copy(address = "192.168.1.11"), InetAddress.getLoopbackAddress())
            moved.newCall(Request.Builder().url("https://$host:${server.port}/v1/status").build())
                .execute().use { assertEquals("moved", it.body.string()) }

            val wrong = PinnedClientFactory.createForTest(endpoint.copy(spkiSha256 = "sha256/" + "A".repeat(43) + "="), InetAddress.getLoopbackAddress())
            assertThrows(SSLException::class.java) {
                wrong.newCall(Request.Builder().url("https://$host:${server.port}/v1/status").build())
                    .execute().use { it.body.string() }
            }
        }
    }

    @Test fun rejectsHostnameMismatchEvenWithCorrectPin() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("other.invalid").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, okhttp3.CertificatePinner.pin(certificate.certificate))
            val client = PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress())
            assertThrows(SSLException::class.java) {
                client.newCall(Request.Builder().url("https://$host:${server.port}/v1/status").build())
                    .execute().use { it.body.string() }
            }
        }
    }
}
