package com.kyoko412.vrcxcompanion.pairing

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyoko412.vrcxcompanion.network.PairRequestReceipt
import com.kyoko412.vrcxcompanion.network.PairRedeemResult
import com.kyoko412.vrcxcompanion.network.PairingHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PairingTransport {
    suspend fun request(deviceName: String): PairRequestReceipt
    suspend fun redeem(receipt: PairRequestReceipt): PairRedeemResult
}

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object Requesting : PairingUiState
    data object Waiting : PairingUiState
    data class Approved(val deviceId: String) : PairingUiState
    data object Rejected : PairingUiState
    data object Expired : PairingUiState
    data object Failed : PairingUiState
}

class PairingViewModel(
    private val transportFactory: (PairingQr) -> PairingTransport,
    private val store: PairingStore,
    private val scope: CoroutineScope? = null,
    private val pollIntervalMs: Long = 1_500,
    private val nowMillis: () -> Long = { SystemClock.elapsedRealtime() }
) : ViewModel() {
    private val mutableState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val state = mutableState.asStateFlow()
    private var pairingJob: Job? = null

    fun request(qr: PairingQr, deviceName: String) {
        pairingJob?.cancel()
        mutableState.value = PairingUiState.Requesting
        pairingJob = (scope ?: viewModelScope).launch {
            try {
                val transport = transportFactory(qr)
                val cleanName = deviceName.filterNot(Char::isISOControl).trim().take(64).ifEmpty { "Android phone" }
                val receipt = transport.request(cleanName)
                val expiresAt = nowMillis() + 120_000
                mutableState.value = PairingUiState.Waiting
                while (nowMillis() < expiresAt) {
                    delay(pollIntervalMs)
                    val result = transport.redeem(receipt)
                    if (result.state == "pending") continue
                    val id = result.deviceId ?: error("Missing device ID")
                    val token = result.token ?: error("Missing device token")
                    store.save(SavedPairing(qr.address, qr.port, qr.spkiSha256, id, token))
                    mutableState.value = PairingUiState.Approved(id)
                    return@launch
                }
                mutableState.value = PairingUiState.Expired
            } catch (error: CancellationException) {
                throw error
            } catch (error: PairingHttpException) {
                mutableState.value = when (error.code) {
                    "pairing_rejected" -> PairingUiState.Rejected
                    "pairing_expired" -> PairingUiState.Expired
                    else -> PairingUiState.Failed
                }
            } catch (_: Exception) {
                mutableState.value = PairingUiState.Failed
            }
        }
    }

    fun unpair() {
        pairingJob?.cancel()
        store.clear()
        mutableState.value = PairingUiState.Idle
    }
}
