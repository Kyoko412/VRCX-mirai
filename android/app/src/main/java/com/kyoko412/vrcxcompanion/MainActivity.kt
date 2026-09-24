package com.kyoko412.vrcxcompanion

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyoko412.vrcxcompanion.data.CompanionRepository
import com.kyoko412.vrcxcompanion.network.HttpPairingTransport
import com.kyoko412.vrcxcompanion.pairing.PairingStore
import com.kyoko412.vrcxcompanion.pairing.PairingUiState
import com.kyoko412.vrcxcompanion.pairing.PairingViewModel
import com.kyoko412.vrcxcompanion.ui.CompanionShell
import com.kyoko412.vrcxcompanion.ui.CompanionColors
import com.kyoko412.vrcxcompanion.ui.CompanionTheme
import com.kyoko412.vrcxcompanion.ui.PairingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0xFF111216.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF111216.toInt()))
        setContent {
            CompanionTheme {
                Box(Modifier.fillMaxSize().background(CompanionColors.background).safeDrawingPadding()) {
                val store = remember { PairingStore.forAndroid(applicationContext) }
                val pairingModel: PairingViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        PairingViewModel({ HttpPairingTransport(it) }, store) as T
                })
                val pairingState by pairingModel.state.collectAsState()
                var paired by remember { mutableStateOf(store.load() != null) }
                var revokedNotice by remember { mutableStateOf(false) }
                LaunchedEffect(pairingState) {
                    if (pairingState is PairingUiState.Approved) {
                        revokedNotice = false
                        paired = true
                    }
                }
                val repository = remember(paired) {
                    if (paired) CompanionRepository.forAndroid(applicationContext) else null
                }
                when {
                    paired && repository != null -> CompanionShell(repository) { revoked ->
                        pairingModel.unpair()
                        revokedNotice = revoked
                        paired = false
                    }
                    pairingState == PairingUiState.Requesting || pairingState == PairingUiState.Waiting ->
                        Column(Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                            CircularProgressIndicator(color = CompanionColors.red)
                            Text("等待电脑批准", style = MaterialTheme.typography.headlineSmall)
                            Text("请在电脑 VRCX 的手机访问设置中批准此手机。请求两分钟后失效。")
                        }
                    pairingState == PairingUiState.Rejected || pairingState == PairingUiState.Expired ||
                        pairingState == PairingUiState.Failed ->
                        Column(Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                            Text(when (pairingState) {
                                PairingUiState.Rejected -> "电脑拒绝了配对请求。"
                                PairingUiState.Expired -> "配对请求已过期，请重新扫码。"
                                else -> "连接失败。请确认电脑与手机在同一 Wi-Fi。"
                            })
                            Button(onClick = { pairingModel.unpair() }) { Text("重新扫码") }
                        }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (revokedNotice) Text("此手机的访问已撤销，请重新配对。",
                            modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                        PairingScreen(onOffer = { pairingModel.request(it, Build.MODEL) })
                    }
                }
                }
            }
        }
    }
}
