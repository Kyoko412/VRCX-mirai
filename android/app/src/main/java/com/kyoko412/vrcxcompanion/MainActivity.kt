package com.kyoko412.vrcxcompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kyoko412.vrcxcompanion.pairing.PairingQr
import com.kyoko412.vrcxcompanion.ui.PairingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var offer by remember { mutableStateOf<PairingQr?>(null) }
                if (offer == null) PairingScreen(onOffer = { offer = it })
                else Text("正在连接 ${offer?.address} 上的 VRCX…")
            }
        }
    }
}
