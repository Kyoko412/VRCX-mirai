package com.kyoko412.vrcxcompanion.network

import com.kyoko412.vrcxcompanion.pairing.PairingQr
import com.kyoko412.vrcxcompanion.pairing.PairingTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PairingHttpException(val code: String) : Exception("Pairing failed: $code")

@Serializable private data class PairRequestBody(val secret: String, val deviceName: String)
@Serializable private data class RedeemBody(val requestId: String, val pollSecret: String)

class CompanionApi(private val endpoint: PinnedEndpoint, private val client: OkHttpClient) {
    private val baseUrl = "https://${PinnedClientFactory.HOST}:${endpoint.port}"

    private suspend fun post(path: String, payload: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + path)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { it.code to it.body.string() }
    }

    suspend fun requestPair(secret: String, deviceName: String): PairRequestReceipt {
        val (code, body) = post("/v1/pair/requests",
            companionJson.encodeToString(PairRequestBody(secret, deviceName.take(64))))
        if (code != 201) throw pairingError(body)
        return companionJson.decodeFromString(body)
    }

    suspend fun redeemPair(receipt: PairRequestReceipt): PairRedeemResult {
        val (code, body) = post("/v1/pair/redeem",
            companionJson.encodeToString(RedeemBody(receipt.requestId, receipt.pollSecret)))
        if (code !in listOf(200, 202)) throw pairingError(body)
        return companionJson.decodeFromString(body)
    }

    private fun pairingError(body: String): PairingHttpException =
        PairingHttpException(runCatching { companionJson.decodeFromString<ApiError>(body).code }
            .getOrDefault("unavailable"))
}

class HttpPairingTransport(private val qr: PairingQr) : PairingTransport {
    private val endpoint = PinnedEndpoint(qr.address, qr.port, qr.spkiSha256)
    private val api = CompanionApi(endpoint, PinnedClientFactory.create(endpoint))
    override suspend fun request(deviceName: String) = api.requestPair(qr.secret, deviceName)
    override suspend fun redeem(receipt: PairRequestReceipt) = api.redeemPair(receipt)
}
