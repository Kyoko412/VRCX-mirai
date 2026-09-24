package com.kyoko412.vrcxcompanion.ui

import com.kyoko412.vrcxcompanion.data.ConnectionSource
import com.kyoko412.vrcxcompanion.network.ApiFailure
import com.kyoko412.vrcxcompanion.network.GameLocationDto
import com.kyoko412.vrcxcompanion.network.Page
import com.kyoko412.vrcxcompanion.network.StatusDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    private val record = GameLocationDto(1, "2026-09-24T02:00:00Z", "wrld_a:1", "wrld_a", "World", 60_000)
    private class FakeSource : ConnectionSource {
        var failure: ApiFailure? = null
        var account = "usr_me"
        override suspend fun status(): StatusDto = StatusDto(1, account, "Computer", "ready")
        override suspend fun gameLog(cursor: String?): Page<GameLocationDto> {
            failure?.let { throw it }
            return Page(account, listOf(GameLocationDto(1, "2026-09-24T02:00:00Z", "wrld_a:1",
                "wrld_a", "World", 60_000)), null)
        }
    }

    @Test fun offlineAfterSuccessClearsCurrentRows() = runTest {
        val source = FakeSource()
        val model = ConnectionViewModel(source, this)
        model.openGameLog()
        advanceUntilIdle()
        assertEquals(listOf(record), model.state.value.records)
        source.failure = ApiFailure.Offline
        model.openGameLog()
        advanceUntilIdle()
        assertTrue(model.state.value.records.isEmpty())
        assertEquals(ConnectionState.Offline, model.state.value.connection)
    }

    @Test fun everyFailureHasActionableStateAndClearsRows() = runTest {
        val model = ConnectionViewModel(FakeSource(), this)
        model.openGameLog(); advanceUntilIdle()
        val failures = listOf(
            ApiFailure.Offline to ConnectionState.Offline,
            ApiFailure.Timeout to ConnectionState.Timeout,
            ApiFailure.Revoked to ConnectionState.Revoked,
            ApiFailure.AccountChanged to ConnectionState.AccountChanged,
            ApiFailure.IncompatibleVersion to ConnectionState.IncompatibleVersion,
            ApiFailure.TlsMismatch to ConnectionState.IdentityChanged,
            ApiFailure.Unavailable to ConnectionState.Unavailable
        )
        failures.forEach { (error, expected) ->
            model.onFailure(error)
            assertTrue(model.state.value.records.isEmpty())
            assertEquals(expected, model.state.value.connection)
        }
        model.onNetworkUnavailable(ConnectionState.WifiMismatch)
        assertEquals(ConnectionState.WifiMismatch, model.state.value.connection)
        model.onNetworkUnavailable(ConnectionState.PermissionDenied)
        assertEquals(ConnectionState.PermissionDenied, model.state.value.connection)
    }

    @Test fun accountChangeWhileShowingDetailsInvalidatesCurrentRecords() = runTest {
        val source = FakeSource()
        val model = ConnectionViewModel(source, this)
        model.openGameLog(); advanceUntilIdle()
        model.onFailure(ApiFailure.AccountChanged)
        assertTrue(model.state.value.records.isEmpty())
        assertEquals(ConnectionState.AccountChanged, model.state.value.connection)
    }
}
