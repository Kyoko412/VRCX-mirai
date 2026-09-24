package com.kyoko412.vrcxcompanion.network

import com.kyoko412.vrcxcompanion.pairing.PairingQr
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.Dns
import okhttp3.OkHttpClient

data class PinnedEndpoint(val address: String, val port: Int, val spkiSha256: String) {
    init {
        require(PairingQr.isPrivateIpv4(address) && port in 1..65535 &&
            spkiSha256.startsWith("sha256/") &&
            runCatching { Base64.getDecoder().decode(spkiSha256.removePrefix("sha256/")).size == 32 }.getOrDefault(false))
    }
}

object PinnedClientFactory {
    const val HOST = "vrcx-companion.invalid"

    fun create(endpoint: PinnedEndpoint): OkHttpClient =
        createInternal(endpoint, InetAddress.getByName(endpoint.address))

    internal fun createForTest(endpoint: PinnedEndpoint, routeAddress: InetAddress): OkHttpClient =
        createInternal(endpoint, routeAddress)

    private fun createInternal(endpoint: PinnedEndpoint, routeAddress: InetAddress): OkHttpClient {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Client certificates are unsupported")
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val certificate = chain.firstOrNull() ?: throw CertificateException("Missing server certificate")
                certificate.checkValidity()
                val pin = "sha256/" + Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
                )
                if (pin != endpoint.spkiSha256) throw CertificateException("Unrecognized desktop key")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val tls = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trust), SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, trust)
            .dns(Dns { hostname ->
                if (hostname != HOST) throw UnknownHostException("Unexpected companion host")
                listOf(routeAddress)
            })
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
