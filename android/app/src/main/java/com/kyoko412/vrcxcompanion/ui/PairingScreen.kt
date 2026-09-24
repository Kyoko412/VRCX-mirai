package com.kyoko412.vrcxcompanion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kyoko412.vrcxcompanion.pairing.InvalidPairingQr
import com.kyoko412.vrcxcompanion.pairing.PairingQr
import com.kyoko412.vrcxcompanion.pairing.PermissionGate
import com.kyoko412.vrcxcompanion.pairing.PermissionState
import com.kyoko412.vrcxcompanion.pairing.QrScanner

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun PairingScreen(onOffer: (PairingQr) -> Unit) {
    val context = LocalContext.current
    var raw by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }
    var lanRequested by remember { mutableStateOf(false) }
    var pendingOffer by remember { mutableStateOf<PairingQr?>(null) }
    var error by remember { mutableStateOf("") }

    val networkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        lanRequested = true
        if (granted) {
            pendingOffer?.let(onOffer)
            pendingOffer = null
            error = ""
        } else error = "请允许附近设备/局域网访问，然后重试。"
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scanning = granted
        cameraDenied = !granted
    }

    fun accept(text: String) {
        val offer = try { PairingQr.parse(text) } catch (_: InvalidPairingQr) {
            error = "配对码无效或已损坏。请在电脑上重新生成。"
            return
        }
        raw = ""
        error = ""
        val granted = Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
        when (PermissionGate.state(Build.VERSION.SDK_INT, granted, lanRequested)) {
            PermissionState.CanConnect -> onOffer(offer)
            PermissionState.NeedsPermission, PermissionState.Denied -> {
                pendingOffer = offer
                networkPermission.launch(LOCAL_NETWORK_PERMISSION)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("连接电脑 VRCX", style = MaterialTheme.typography.headlineSmall)
        Text("电脑和手机需连接同一 Wi-Fi。先在电脑 VRCX 设置中启用手机访问并显示二维码。")
        Button(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                scanning = true
            } else cameraPermission.launch(Manifest.permission.CAMERA)
        }) { Text(if (scanning) "重新扫码" else "扫描电脑二维码") }
        if (scanning) QrScanner(onCode = { scanning = false; accept(it) }, onError = {
            scanning = false
            error = "摄像头无法使用，请粘贴配对码。"
        })
        if (cameraDenied) Text("摄像头权限未开启。可在系统设置中允许，或粘贴配对码。")
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            label = { Text("粘贴配对码") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { accept(raw) }, enabled = raw.isNotBlank()) { Text("使用配对码") }
        if (pendingOffer != null && lanRequested) {
            Button(onClick = { networkPermission.launch(LOCAL_NETWORK_PERMISSION) }) { Text("重试局域网权限") }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}
