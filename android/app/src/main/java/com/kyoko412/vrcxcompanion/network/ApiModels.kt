package com.kyoko412.vrcxcompanion.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val companionJson = Json { ignoreUnknownKeys = true }

class UnsupportedApiVersion(val version: Int) : IllegalArgumentException("Unsupported companion API version $version")

@Serializable
data class StatusDto(
    val apiVersion: Int,
    val accountId: String,
    val computerName: String,
    val syncState: String
)

fun parseStatus(raw: String): StatusDto = companionJson.decodeFromString<StatusDto>(raw).also {
    if (it.apiVersion != 1) throw UnsupportedApiVersion(it.apiVersion)
}

@Serializable
data class Page<T>(val accountId: String, val items: List<T>, val nextCursor: String?)

@Serializable
data class FriendDto(val id: String, val displayName: String)

@Serializable
data class VisitDto(
    val eventKey: String,
    val worldId: String?,
    val worldName: String?,
    val location: String?,
    val enteredAt: String?,
    val exitedAt: String?,
    val durationMs: Long?,
    val observedAt: String,
    val visitCount: Int
)

@Serializable
data class EncounterDto(
    val visitKey: String,
    val worldId: String?,
    val worldName: String?,
    val location: String?,
    val observedAt: String
)

@Serializable
data class EncounterPage(
    val accountId: String,
    val qualifiedCount: Int,
    val unknownCount: Int,
    val items: List<EncounterDto>,
    val nextCursor: String?
)

@Serializable
data class BioChangeDto(val id: Long, val previousBio: String?, val bio: String?, val observedAt: String)

@Serializable
data class GameLocationDto(
    val id: Long,
    val createdAt: String,
    val location: String,
    val worldId: String?,
    val worldName: String?,
    val durationMs: Long?
)

@Serializable
data class ApiError(val code: String, val message: String)

@Serializable
data class PairRequestReceipt(val requestId: String, val pollSecret: String)

@Serializable
data class PairRedeemResult(val state: String? = null, val deviceId: String? = null, val token: String? = null)
