package com.zer0state.zzthat.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the phone has a usable path to the network (US6). Offline, a cached
 * public view is labeled offline and writes are refused. A Demo build makes
 * no network call, so it is never offline.
 */
class Connectivity(online: Boolean = true) {
    var isOnline by mutableStateOf(online)
        private set

    private var watching = false

    fun set(online: Boolean) {
        isOnline = online
    }

    /** Starts watching the default network. Not used in Demo builds or tests. */
    fun watch(context: Context) {
        if (watching) return
        watching = true
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val main = Handler(Looper.getMainLooper())
        isOnline = manager.activeNetwork?.let(manager::getNetworkCapabilities)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    main.post { isOnline = online }
                }

                override fun onLost(network: Network) {
                    main.post { isOnline = false }
                }
            },
        )
    }
}
