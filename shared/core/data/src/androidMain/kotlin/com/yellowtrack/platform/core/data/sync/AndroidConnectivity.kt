package com.yellowtrack.platform.core.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The phone's answer, which is the one that matters most.
 *
 * A photographer's phone moves between signal and no signal several times in a day, and it is
 * the device most likely to be woken at a venue, used for ten minutes, and put away again.
 *
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED] rather than merely connected: Android knows
 * the difference between "associated with a Wi-Fi network" and "that network answered a probe",
 * and a venue's guest Wi-Fi behind a sign-in page is the exact case where the weaker answer
 * would send us reconciling into a captive portal.
 *
 * `onLost` reports false rather than nothing. It changes no behaviour today — [Synchroniser]
 * only acts on the transition into online — but a flow that reports one edge and not the other
 * cannot say whether the transition happened.
 */
class AndroidConnectivity(
    context: Context,
) : Connectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override val online: Flow<Boolean> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(true)
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }
                }

            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build()

            manager.registerNetworkCallback(request, callback)

            awaitClose { manager.unregisterNetworkCallback(callback) }
        }
}
