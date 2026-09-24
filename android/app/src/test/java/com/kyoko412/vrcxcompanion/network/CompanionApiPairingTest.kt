package com.kyoko412.vrcxcompanion.network

import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CertificatePinner
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class CompanionApiPairingTest {
    @Test fun requestAndRedeemUsePinnedHttpsAndExactProtocol() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, CertificatePinner.pin(certificate.certificate))
            val api = CompanionApi(endpoint, PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress()))
            server.enqueue(MockResponse.Builder().code(201)
                .body("""{"requestId":"req1","pollSecret":"poll1"}""").build())
            val receipt = api.requestPair("once-secret", "Phone")
            assertEquals("req1", receipt.requestId)
            val request = server.takeRequest()
            assertEquals("/v1/pair/requests", request.target)
            assertFalse(request.body?.utf8().orEmpty().contains("accountId"))
            assertEquals(true, request.body?.utf8().orEmpty().contains("once-secret"))

            server.enqueue(MockResponse.Builder().code(202).body("""{"state":"pending"}""").build())
            assertEquals("pending", api.redeemPair(receipt).state)
            server.enqueue(MockResponse.Builder().code(200)
                .body("""{"deviceId":"dev1","token":"device-token"}""").build())
            assertEquals("device-token", api.redeemPair(receipt).token)
        }
    }

    @Test fun expiredOfferMapsToExpiredCode() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(PinnedClientFactory.HOST).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            server.start()
            val endpoint = PinnedEndpoint("192.168.1.10", server.port, CertificatePinner.pin(certificate.certificate))
            val api = CompanionApi(endpoint, PinnedClientFactory.createForTest(endpoint, InetAddress.getLoopbackAddress()))
            server.enqueue(MockResponse.Builder().code(400)
                .body("""{"code":"pairing_expired","message":"Pairing request expired"}""").build())
            val failure = assertThrows(PairingHttpException::class.java) {
                runBlocking { api.requestPair("expired", "Phone") }
            }
            assertEquals("pairing_expired", failure.code)
        }
    }
}
