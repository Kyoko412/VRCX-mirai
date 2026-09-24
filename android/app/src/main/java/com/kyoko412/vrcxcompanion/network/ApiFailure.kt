package com.kyoko412.vrcxcompanion.network

import java.io.IOException

sealed class ApiFailure(message: String) : IOException(message) {
    data object Unauthorized : ApiFailure("Device token was not accepted")
    data object Revoked : ApiFailure("Device access was revoked or belongs to another account")
    data object SessionChanged : ApiFailure("Desktop account changed during the request")
    data object AccountChanged : ApiFailure("Desktop account changed")
    data object IncompatibleVersion : ApiFailure("Companion API version is unsupported")
    data object InvalidRequest : ApiFailure("Companion request is invalid")
    data object NotFound : ApiFailure("Record is unavailable")
    data object Unavailable : ApiFailure("Desktop data is unavailable")
    data object Offline : ApiFailure("Computer is unreachable")
    data object Timeout : ApiFailure("Computer did not respond in time")
    data object TlsMismatch : ApiFailure("Computer identity did not match the pairing")
    data object MalformedResponse : ApiFailure("Desktop response could not be read")
}
