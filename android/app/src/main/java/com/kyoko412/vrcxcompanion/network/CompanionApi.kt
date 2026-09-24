package com.kyoko412.vrcxcompanion.network

import com.kyoko412.vrcxcompanion.pairing.PairingQr
import com.kyoko412.vrcxcompanion.pairing.PairingTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class PairingHttpException(val code: String) : Exception("Pairing failed: $code")

@Serializable private data class PairRequestBody(val secret: String, val deviceName: String)
@Serializable private data class RedeemBody(val requestId: String, val pollSecret: String)

class CompanionApi(private val endpoint: PinnedEndpoint, private val client: OkHttpClient) {
    private val baseUrl = "https://${PinnedClientFactory.HOST}:${endpoint.port}"
    private val userId = Regex("usr_[A-Za-z0-9_-]{1,64}")

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

    private suspend fun get(token: String, segments: List<String>, cursor: String? = null,
                            search: String? = null): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
        segments.forEach(url::addPathSegment)
        if (segments.last() != "status") {
            url.addQueryParameter("limit", "50")
            if (cursor != null) url.addQueryParameter("cursor", cursor)
            if (search != null) url.addQueryParameter("search", search)
        }
        val request = Request.Builder().url(url.build())
            .header("Authorization", "Bearer $token").get().build()
        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> response.body.string()
                    400 -> throw ApiFailure.InvalidRequest
                    401 -> throw ApiFailure.Unauthorized
                    403 -> throw ApiFailure.Revoked
                    404 -> throw ApiFailure.NotFound
                    409 -> throw ApiFailure.SessionChanged
                    503 -> throw ApiFailure.Unavailable
                    else -> throw ApiFailure.Unavailable
                }
            }
        } catch (error: ApiFailure) { throw error }
        catch (_: SSLException) { throw ApiFailure.TlsMismatch }
        catch (_: SocketTimeoutException) { throw ApiFailure.Timeout }
        catch (_: IOException) { throw ApiFailure.Offline }
    }

    private fun validFriend(id: String): String {
        require(userId.matches(id)) { "Invalid friend ID" }
        return id
    }

    suspend fun status(token: String): StatusDto = try {
        parseStatus(get(token, listOf("v1", "status")))
    } catch (_: UnsupportedApiVersion) { throw ApiFailure.IncompatibleVersion }
      catch (_: SerializationException) { throw ApiFailure.MalformedResponse }

    suspend fun friends(token: String, search: String?, cursor: String?): Page<FriendDto> =
        decodePage(get(token, listOf("v1", "friends"), cursor, search))

    suspend fun worldVisits(token: String, friendId: String, cursor: String?): Page<VisitDto> =
        decodePage(get(token, listOf("v1", "friends", validFriend(friendId), "world-visits"), cursor))

    suspend fun encounters(token: String, friendId: String, cursor: String?): EncounterPage =
        decodeResponse(get(token, listOf("v1", "friends", validFriend(friendId), "encounters"), cursor))

    suspend fun bioHistory(token: String, friendId: String, cursor: String?): Page<BioChangeDto> =
        decodePage(get(token, listOf("v1", "friends", validFriend(friendId), "bio-history"), cursor))

    suspend fun gameLog(token: String, cursor: String?): Page<GameLocationDto> =
        decodePage(get(token, listOf("v1", "me", "game-log"), cursor))

    private inline fun <reified T> decodePage(body: String): Page<T> = decodeResponse(body)

    private inline fun <reified T> decodeResponse(body: String): T = try {
        companionJson.decodeFromString<T>(body)
    } catch (_: SerializationException) { throw ApiFailure.MalformedResponse }
}

class HttpPairingTransport(private val qr: PairingQr) : PairingTransport {
    private val endpoint = PinnedEndpoint(qr.address, qr.port, qr.spkiSha256)
    private val api = CompanionApi(endpoint, PinnedClientFactory.create(endpoint))
    override suspend fun request(deviceName: String) = api.requestPair(qr.secret, deviceName)
    override suspend fun redeem(receipt: PairRequestReceipt) = api.redeemPair(receipt)
}
