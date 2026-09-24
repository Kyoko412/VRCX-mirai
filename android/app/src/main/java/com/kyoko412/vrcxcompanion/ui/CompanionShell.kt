package com.kyoko412.vrcxcompanion.ui

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyoko412.vrcxcompanion.data.CompanionRepository
import com.kyoko412.vrcxcompanion.network.ApiFailure

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun CompanionShell(repository: CompanionRepository, onUnpair: (Boolean) -> Unit) {
    val context = LocalContext.current
    val key = remember(repository) { repository.hashCode().toString() }
    val friendsModel: FriendsViewModel = viewModel(key = "friends-$key", factory = modelFactory { FriendsViewModel(repository) })
    val historyModel: HistoryViewModel = viewModel(key = "history-$key", factory = modelFactory { HistoryViewModel(repository) })
    val connectionModel: ConnectionViewModel = viewModel(key = "connection-$key",
        factory = modelFactory { ConnectionViewModel(repository) })
    val friends by friendsModel.state.collectAsState()
    val history by historyModel.state.collectAsState()
    val connection by connectionModel.state.collectAsState()
    var page by remember(repository) { mutableStateOf("home") }
    var friendName by remember(repository) { mutableStateOf("") }
    var rescanError by remember(repository) { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) connectionModel.refreshStatus()
        else connectionModel.onNetworkUnavailable(ConnectionState.PermissionDenied)
    }

    fun readyToRead(): Boolean {
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            connectionModel.onNetworkUnavailable(ConnectionState.PermissionDenied)
            permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
            return false
        }
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            connectionModel.onNetworkUnavailable(ConnectionState.WifiMismatch)
            return false
        }
        return true
    }

    LaunchedEffect(repository) {
        if (readyToRead()) connectionModel.refreshStatus()
    }
    LaunchedEffect(friends.error, history.error) {
        val failure = friends.error ?: history.error
        if (failure != null) {
            connectionModel.onFailure(failure)
            friendsModel.clear()
            historyModel.clear()
        }
    }
    LaunchedEffect(connection.connection) {
        if (connection.connection != ConnectionState.Ready && connection.connection != ConnectionState.Loading) {
            friendsModel.clear()
            historyModel.clear()
            if (page != "game") page = "home"
        }
        if (connection.connection == ConnectionState.Revoked) {
            repository.unpair()
            onUnpair(true)
        }
    }

    when (page) {
        "rescan" -> PairingScreen(onOffer = { qr ->
            rescanError = ""
            try {
                repository.updateAddress(qr.address, qr.port, qr.spkiSha256)
                friendsModel.clear()
                historyModel.clear()
                page = "home"
                connectionModel.refreshStatus()
            } catch (_: ApiFailure.TlsMismatch) {
                rescanError = "证书指纹与原电脑不一致，地址未更新。请核对电脑。"
            } catch (_: IllegalArgumentException) {
                rescanError = "新地址无效，请重新扫描电脑二维码。"
            }
        }, rescanMode = true, onBack = { page = "home" }, externalError = rescanError)
        "friends" -> FriendsScreen(friends, friendsModel::search,
            onOpen = { id, name -> friendName = name; historyModel.open(id); page = "detail" },
            onLoadMore = friendsModel::loadMore, onRefresh = { if (readyToRead()) friendsModel.refresh() },
            onBack = { page = "home" })
        "detail" -> FriendDetailScreen(friendName, history,
            onBack = { page = "friends" }, onTab = historyModel::selectTab,
            onLoadMore = historyModel::loadMore,
            onRefresh = { if (readyToRead()) historyModel.refresh() })
        "game" -> GameLogScreen(connection, onBack = { page = "home" },
            onRefresh = { if (readyToRead()) connectionModel.openGameLog() },
            onLoadMore = connectionModel::loadMore)
        else -> HomeScreen(connection.status?.computerName ?: "未知",
            connection.status?.accountId ?: "未知", connection.lastSuccessfulRead,
            connection.connection.label,
            onFriends = { if (readyToRead()) { friendsModel.refresh(); page = "friends" } },
            onGameLog = { if (readyToRead()) { connectionModel.openGameLog(); page = "game" } },
            onRefresh = { if (readyToRead()) connectionModel.refreshStatus() },
            onRescan = { rescanError = ""; page = "rescan" },
            onUnpair = { repository.unpair(); onUnpair(false) })
    }
}

private fun <T : ViewModel> modelFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }
