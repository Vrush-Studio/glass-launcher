@file:Suppress("DEPRECATION") // Android 7 Wi-Fi API: the one the Fire TV runs.

package com.fares.glass.sys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fares.glass.R

data class WifiNet(val ssid: String, val level: Int, val secure: Boolean, val saved: Boolean, val connected: Boolean)

/** Wi-Fi state and commands for the Control Center (Wi-Fi permissions, plus location for scanning). */
class WifiController(context: Context) {
    private val app = context.applicationContext
    private val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    var enabled by mutableStateOf(false)
        private set
    var current by mutableStateOf<String?>(null)
        private set
    var ip by mutableStateOf<String?>(null)
        private set
    var level by mutableIntStateOf(0)
        private set
    var networks by mutableStateOf<List<WifiNet>>(emptyList())
        private set
    /** Message shown under the list (connecting, failure, scan unavailable…). */
    var status by mutableStateOf<String?>(null)

    private var listening = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    fun start() {
        if (!listening) {
            app.registerReceiver(receiver, IntentFilter().apply {
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.RSSI_CHANGED_ACTION)
            })
            listening = true
        }
        refresh()
        scan()
    }

    fun stop() {
        if (listening) runCatching { app.unregisterReceiver(receiver) }
        listening = false
    }

    fun scan() {
        if (wm.isWifiEnabled) runCatching { wm.startScan() }
    }

    fun refresh() {
        enabled = wm.isWifiEnabled
        val info = wm.connectionInfo
        val ssid = info?.ssid?.trim('"')?.takeIf { enabled && it.isNotBlank() && it != "<unknown ssid>" && info.networkId != -1 }
        current = ssid
        ip = info?.ipAddress?.takeIf { it != 0 }?.let { "${it and 0xFF}.${it shr 8 and 0xFF}.${it shr 16 and 0xFF}.${it shr 24 and 0xFF}" }
        level = if (ssid != null) WifiManager.calculateSignalLevel(info.rssi, 5) else 0
        if (ssid != null && status == app.getString(R.string.wifi_connecting, ssid)) status = null
        val saved = runCatching { wm.configuredNetworks.orEmpty().mapNotNull { it.SSID?.trim('"') }.toSet() }.getOrDefault(emptySet())
        val results = try {
            wm.scanResults.orEmpty()
        } catch (e: SecurityException) {
            status = app.getString(R.string.wifi_scan_no_location)
            emptyList()
        }
        networks = results
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (name, list) ->
                val best = list.maxBy { it.level }
                WifiNet(
                    ssid = name,
                    level = WifiManager.calculateSignalLevel(best.level, 5),
                    secure = best.capabilities.contains("WPA") || best.capabilities.contains("WEP"),
                    saved = name in saved,
                    connected = name == ssid,
                )
            }
            .sortedWith(compareByDescending<WifiNet> { it.connected }.thenByDescending { it.saved }.thenByDescending { it.level })
    }

    /** Android 10+: apps can no longer turn Wi-Fi on or off, or save networks themselves. */
    val canConnect get() = android.os.Build.VERSION.SDK_INT < 29

    /** Turns Wi-Fi on or off. Returns false if the system refuses (Android 10+). */
    fun turn(on: Boolean): Boolean {
        if (!canConnect) return false
        val ok = runCatching { wm.setWifiEnabled(on) }.onFailure { Log.w("Glass", "Wi-Fi", it) }.getOrDefault(false)
        if (ok) {
            enabled = on
            if (!on) networks = emptyList()
        }
        return ok
    }

    /** Saved or open network: connect directly. Otherwise a password is needed. */
    fun needsPassword(net: WifiNet) = net.secure && !net.saved

    fun connect(net: WifiNet, password: String? = null) {
        status = app.getString(R.string.wifi_connecting, net.ssid)
        val quoted = "\"${net.ssid}\""
        val existing = runCatching { wm.configuredNetworks.orEmpty().firstOrNull { it.SSID == quoted } }.getOrNull()
        val id = if (existing != null && password == null) {
            existing.networkId
        } else {
            existing?.let { wm.removeNetwork(it.networkId) }
            wm.addNetwork(WifiConfiguration().apply {
                SSID = quoted
                if (net.secure && password != null) {
                    preSharedKey = "\"$password\""
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            })
        }
        if (id == -1) {
            status = app.getString(R.string.wifi_save_failed)
            return
        }
        val ok = wm.enableNetwork(id, true) && wm.reconnect()
        status = app.getString(if (ok) R.string.wifi_connecting else R.string.wifi_connect_failed, net.ssid)
    }
}
