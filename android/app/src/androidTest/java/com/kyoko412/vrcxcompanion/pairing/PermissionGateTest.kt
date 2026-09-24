package com.kyoko412.vrcxcompanion.pairing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionGateTest {
    @Test fun denialBlocksConnectionUntilPermissionGranted() {
        assertEquals(PermissionState.Denied, PermissionGate.state(37, false, true))
        assertEquals(PermissionState.CanConnect, PermissionGate.state(37, true, true))
    }
}
