package com.kyoko412.vrcxcompanion.pairing

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class InvalidPairingQr : IllegalArgumentException("Invalid VRCX pairing code")

@Serializable
data class PairingQr(
    val v: Int,
    val address: String,
    val port: Int,
    val host: String,
    val spkiSha256: String,
    val secret: String
) {
    companion object {
        private val strictJson = Json { ignoreUnknownKeys = false }
        private val secretPattern = Regex("[A-Za-z0-9_-]{43}")

        fun parse(raw: String): PairingQr {
            if (raw.toByteArray(Charsets.UTF_8).size > 2048) throw InvalidPairingQr()
            try {
                val qr = strictJson.decodeFromString<PairingQr>(raw)
                if (qr.v != 1 || qr.host != "vrcx-companion.invalid" || qr.port !in 1..65535 ||
                    !isPrivateIpv4(qr.address) || !isValidPin(qr.spkiSha256) ||
                    !secretPattern.matches(qr.secret) ||
                    Base64.getUrlDecoder().decode(qr.secret).size != 32
                ) throw InvalidPairingQr()
                return qr
            } catch (_: Exception) {
                throw InvalidPairingQr()
            }
        }

        fun isPrivateIpv4(address: String): Boolean {
            val parts = address.split('.')
            if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 ||
                        it.any { char -> !char.isDigit() } ||
                        (it.length > 1 && it.startsWith('0')) }) return false
            val bytes = parts.map { it.toIntOrNull() ?: return false }
            if (bytes.any { it !in 0..255 }) return false
            return bytes[0] == 10 ||
                (bytes[0] == 172 && bytes[1] in 16..31) ||
                (bytes[0] == 192 && bytes[1] == 168)
        }

        private fun isValidPin(pin: String): Boolean {
            if (!pin.startsWith("sha256/")) return false
            val encoded = pin.removePrefix("sha256/")
            return try {
                val decoded = Base64.getDecoder().decode(encoded)
                decoded.size == 32 && Base64.getEncoder().encodeToString(decoded) == encoded
            } catch (_: IllegalArgumentException) { false }
        }
    }
}
