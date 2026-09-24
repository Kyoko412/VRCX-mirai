package com.kyoko412.vrcxcompanion.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionGateJvmTest {
    @Test fun localNetworkPermissionOnlyGatesAndroid17AndLater() {
        assertEquals(PermissionState.CanConnect, PermissionGate.state(36, false, false))
        assertEquals(PermissionState.NeedsPermission, PermissionGate.state(37, false, false))
        assertEquals(PermissionState.Denied, PermissionGate.state(37, false, true))
        assertEquals(PermissionState.CanConnect, PermissionGate.state(37, true, true))
    }
}
