package com.kyoko412.vrcxcompanion.pairing

enum class PermissionState { CanConnect, NeedsPermission, Denied }

object PermissionGate {
    fun state(apiLevel: Int, localNetworkGranted: Boolean, requestedBefore: Boolean): PermissionState = when {
        apiLevel < 37 || localNetworkGranted -> PermissionState.CanConnect
        requestedBefore -> PermissionState.Denied
        else -> PermissionState.NeedsPermission
    }
}
