package com.kyoko412.vrcxcompanion.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class PairingQrTest {
    private val pin = "sha256/" + Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 2 })

    private fun qr(address: String = "192.168.1.10", port: Int = 34682, host: String = "vrcx-companion.invalid",
                   pin: String = this.pin, secret: String = this.secret) =
        """{"v":1,"address":"$address","port":$port,"host":"$host","spkiSha256":"$pin","secret":"$secret"}"""

    @Test fun acceptsOnlyPrivateLanOffer() {
        assertEquals("192.168.1.10", PairingQr.parse(qr()).address)
        assertEquals("172.16.0.1", PairingQr.parse(qr(address = "172.16.0.1")).address)
        assertEquals("10.0.0.2", PairingQr.parse(qr(address = "10.0.0.2")).address)
    }

    @Test fun rejectsInvalidNetworkTargetsAndSecrets() {
        for (raw in listOf(
            "not-json", "https://evil.example", qr(address = "8.8.8.8"), qr(address = "::1"),
            qr(address = "127.0.0.1"), qr(address = "192.168.01.10"), qr(port = 0),
            qr(host = "evil.example"), qr(pin = "sha256/bad"), qr(secret = ""),
            qr() + "x", "x".repeat(2049)
        )) assertThrows(InvalidPairingQr::class.java) { PairingQr.parse(raw) }
    }
}
