package com.kyoko412.vrcxcompanion

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyoko412.vrcxcompanion.network.HttpPairingTransport
import com.kyoko412.vrcxcompanion.pairing.PairingStore
import com.kyoko412.vrcxcompanion.pairing.PairingUiState
import com.kyoko412.vrcxcompanion.pairing.PairingViewModel
import com.kyoko412.vrcxcompanion.ui.PairingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val store = remember { PairingStore.forAndroid(applicationContext) }
                val model: PairingViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        PairingViewModel({ HttpPairingTransport(it) }, store) as T
                })
                val state by model.state.collectAsState()
                var paired by remember { mutableStateOf(store.load() != null) }
                LaunchedEffect(state) { if (state is PairingUiState.Approved) paired = true }
                when {
                    paired -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("已配对电脑 VRCX", style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = { model.unpair(); paired = false }) { Text("解除配对") }
                    }
                    state == PairingUiState.Requesting || state == PairingUiState.Waiting ->
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("等待电脑批准", style = MaterialTheme.typography.headlineSmall)
                            Text("请在电脑 VRCX 的手机访问设置中批准此手机。请求两分钟后失效。")
                        }
                    state == PairingUiState.Rejected || state == PairingUiState.Expired || state == PairingUiState.Failed ->
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(when (state) {
                                PairingUiState.Rejected -> "电脑拒绝了配对请求。"
                                PairingUiState.Expired -> "配对请求已过期，请重新扫码。"
                                else -> "连接失败。请确认电脑与手机在同一 Wi-Fi。"
                            })
                            Button(onClick = { model.unpair() }) { Text("重新扫码") }
                        }
                    else -> PairingScreen(onOffer = { model.request(it, Build.MODEL) })
                }
            }
        }
    }
}
