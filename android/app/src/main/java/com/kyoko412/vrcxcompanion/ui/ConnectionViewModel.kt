package com.kyoko412.vrcxcompanion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyoko412.vrcxcompanion.data.ConnectionSource
import com.kyoko412.vrcxcompanion.data.PageAccumulator
import com.kyoko412.vrcxcompanion.network.ApiFailure
import com.kyoko412.vrcxcompanion.network.GameLocationDto
import com.kyoko412.vrcxcompanion.network.StatusDto
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val connection: ConnectionState = ConnectionState.Loading,
    val status: StatusDto? = null,
    val lastSuccessfulRead: String? = null,
    val records: List<GameLocationDto> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false
)

class ConnectionViewModel(private val source: ConnectionSource, private val scope: CoroutineScope? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(ConnectionUiState())
    val state = mutableState.asStateFlow()
    private val pages = PageAccumulator<GameLocationDto> { it.id.toString() }
    private var request: Job? = null

    fun refreshStatus() {
        request?.cancel()
        request = (scope ?: viewModelScope).launch {
            mutableState.value = mutableState.value.copy(connection = ConnectionState.Loading, loading = true)
            try {
                val status = source.status()
                mutableState.value = mutableState.value.copy(status = status, connection = ConnectionState.Ready,
                    lastSuccessfulRead = Instant.now().toString(), loading = false)
            } catch (error: CancellationException) { throw error }
              catch (error: ApiFailure) { onFailure(error) }
        }
    }

    fun openGameLog() = readGameLog(null)

    fun loadMore() {
        if (mutableState.value.loading) return
        mutableState.value.nextCursor?.let(::readGameLog)
    }

    private fun readGameLog(cursor: String?) {
        request?.cancel()
        request = (scope ?: viewModelScope).launch {
            // A previous page must never look current while a fresh read is pending.
            if (cursor == null) { pages.clear(); mutableState.value = mutableState.value.copy(records = emptyList(), nextCursor = null) }
            mutableState.value = mutableState.value.copy(connection = ConnectionState.Loading, loading = true)
            try {
                val status = source.status()
                val page = source.gameLog(cursor)
                if (page.accountId != status.accountId) throw ApiFailure.AccountChanged
                pages.accept(page, status.accountId, reset = cursor == null)
                mutableState.value = mutableState.value.copy(status = status, records = pages.items,
                    nextCursor = page.nextCursor, lastSuccessfulRead = Instant.now().toString(),
                    connection = ConnectionState.Ready, loading = false)
            } catch (error: CancellationException) { throw error }
              catch (error: ApiFailure) { onFailure(error) }
        }
    }

    fun onNetworkUnavailable(state: ConnectionState) {
        require(state == ConnectionState.WifiMismatch || state == ConnectionState.PermissionDenied)
        clear(state)
    }

    fun onFailure(error: ApiFailure) {
        val state = when (error) {
            ApiFailure.Offline -> ConnectionState.Offline
            ApiFailure.Timeout -> ConnectionState.Timeout
            ApiFailure.Unauthorized, ApiFailure.Revoked -> ConnectionState.Revoked
            ApiFailure.AccountChanged, ApiFailure.SessionChanged -> ConnectionState.AccountChanged
            ApiFailure.IncompatibleVersion -> ConnectionState.IncompatibleVersion
            ApiFailure.TlsMismatch -> ConnectionState.IdentityChanged
            else -> ConnectionState.Unavailable
        }
        clear(state)
    }

    private fun clear(connection: ConnectionState) {
        request?.cancel()
        pages.clear()
        mutableState.value = mutableState.value.copy(connection = connection,
            status = if (connection == ConnectionState.AccountChanged || connection == ConnectionState.Revoked) null
                else mutableState.value.status,
            records = emptyList(), nextCursor = null, loading = false)
    }
}
