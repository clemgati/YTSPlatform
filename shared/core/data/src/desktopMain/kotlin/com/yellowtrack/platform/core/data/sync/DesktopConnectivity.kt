package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.common.coroutines.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.NetworkInterface
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Polled, because the JVM has no callback for this.
 *
 * Android has `NetworkCallback` and Apple has `NWPathMonitor`; the JVM has neither, which is
 * why desktop was left on [Connectivity.Unknown] when the other three were written. That was
 * a poor trade: a laptop closing its lid and reopening on a different network is the same
 * case the trigger exists for, and desktop is the build a studio spends its working day in.
 *
 * ## What it looks at
 *
 * Whether any network interface is up and carries a real address. Not whether the server can
 * be reached — that would mean a request every few seconds to answer a question the next sync
 * answers anyway, and the interface is the thing that actually changes when a lid opens or a
 * cable is pulled.
 *
 * It will therefore say "online" for a machine sitting on a router with no route out. That is
 * allowed, and is why [Connectivity] is documented as advisory: this only ever brings a run
 * forward, and a run that turns out to be pointless costs one request. The alternative — a
 * signal so cautious it stays silent — costs up to an hour of a stale screen, which is the
 * fault being fixed.
 *
 * Loopback is excluded, and so is an interface with nothing but a link-local address: a
 * machine with no network at all still reports `lo` as up, and macOS keeps assorted virtual
 * interfaces up permanently. Requiring a routable address is what makes the difference between
 * a signal and a constant.
 *
 * ## Where it will not fire
 *
 * A tunnel that stays up while the network underneath it goes away. This machine carries a
 * `utun10` holding `fd0d:…`, a unique local address, which Java reports as neither loopback
 * nor link-local — so it satisfies the test on its own. If Wi-Fi drops and that tunnel does
 * not, no transition is seen.
 *
 * Left alone deliberately. Excluding interfaces by name (`utun`, `tun`, `ppp`) is a guess
 * about the machine that would be wrong somewhere, and excluding unique local addresses would
 * take a plain IPv6 home network with it. The cost of missing a transition is the behaviour
 * this feature replaced — the timer, and up to an hour — so a miss degrades to yesterday
 * rather than to something worse.
 */
class DesktopConnectivity(
    private val interval: Duration = POLL_INTERVAL,
    private val hasUsableInterface: () -> Boolean = ::anyInterfaceCarriesAnAddress,
) : Connectivity {
    /**
     * Emits the current answer, then only when it changes.
     *
     * The first emission is on purpose. A machine that starts up connected reports `true`
     * once, which brings one sync forward at launch — and [Synchroniser] holds a mutex, so
     * that costs nothing when the periodic loop's own first run is already in flight.
     */
    override val online: Flow<Boolean> =
        flow {
            while (true) {
                emit(hasUsableInterface())
                delay(interval)
            }
        }.distinctUntilChanged()
            .flowOn(ioDispatcher)

    companion object {
        /**
         * Five seconds, which is a cost worth stating: enumerating interfaces is a local
         * call with no I/O, so this is cheaper than the sync it might save waiting an hour
         * for. Slow enough that a flapping adapter does not become a second timer, because
         * only *changes* leave this flow.
         */
        val POLL_INTERVAL: Duration = 5.seconds
    }
}

/**
 * Whether anything on this machine could carry traffic off it.
 *
 * A link-local address (`169.254.x.x`, `fe80::`) means the interface came up and never got
 * configured, which is a machine that cannot reach a server — so it does not count.
 */
private fun anyInterfaceCarriesAnAddress(): Boolean =
    runCatching {
        NetworkInterface
            .getNetworkInterfaces()
            .toList()
            .any { candidate ->
                candidate.isUp &&
                    !candidate.isLoopback &&
                    candidate.inetAddresses.toList().any { address ->
                        !address.isLoopbackAddress && !address.isLinkLocalAddress
                    }
            }
        // A machine that will not answer questions about its own interfaces is not a machine
        // to declare offline: that would suppress nothing (this never suppresses) but would
        // also never report a reconnection.
    }.getOrDefault(true)
