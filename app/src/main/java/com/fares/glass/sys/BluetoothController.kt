@file:Suppress("DEPRECATION", "MissingPermission") // API Bluetooth d'Android 7 ; permissions accordées par ADB.

package com.fares.glass.sys

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class BtDevice(
    val address: String,
    val name: String,
    val bonded: Boolean,
    val connected: Boolean,
    /** Télécommande / manette : on ne propose jamais de l'oublier depuis Glass. */
    val protected: Boolean,
    val kind: String,
)

/** État et commandes Bluetooth pour le Centre de contrôle. */
class BluetoothController(context: Context) {
    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val seen = LinkedHashMap<String, BluetoothDevice>()

    val available get() = adapter != null
    var enabled by mutableStateOf(false)
        private set
    var paired by mutableStateOf<List<BtDevice>>(emptyList())
        private set
    var found by mutableStateOf<List<BtDevice>>(emptyList())
        private set
    var discovering by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)

    private var listening = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.let { seen[it.address] = it }
            }
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                status = when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                    BluetoothDevice.BOND_BONDING -> "Jumelage avec ${dev?.name ?: "l'appareil"}…"
                    BluetoothDevice.BOND_BONDED -> "${dev?.name ?: "Appareil"} jumelé"
                    else -> status
                }
            }
            refresh()
        }
    }

    fun start() {
        if (!listening) {
            app.registerReceiver(receiver, IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            })
            listening = true
        }
        refresh()
    }

    fun stop() {
        if (listening) runCatching { app.unregisterReceiver(receiver) }
        listening = false
        runCatching { if (adapter?.isDiscovering == true) adapter.cancelDiscovery() }
    }

    fun refresh() {
        val a = adapter ?: return
        try {
            enabled = a.isEnabled
            discovering = a.isDiscovering
            val bonded = if (enabled) a.bondedDevices.orEmpty() else emptySet()
            paired = bonded.map { it.toBt() }.sortedWith(compareByDescending<BtDevice> { it.connected }.thenBy { it.name })
            val bondedAddresses = bonded.map { it.address }.toSet()
            found = seen.values.filter { it.address !in bondedAddresses && !it.name.isNullOrBlank() }.map { it.toBt() }
        } catch (e: SecurityException) {
            status = "Permission Bluetooth manquante"
        }
    }

    /** Allume ou coupe le Bluetooth. Retourne false si le système le refuse (Android 13+). */
    fun turn(on: Boolean): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33) return false
        val ok = runCatching { if (on) adapter?.enable() else adapter?.disable() }
            .onFailure { Log.w("Glass", "Bluetooth", it) }.getOrNull() == true
        if (ok) {
            enabled = on
            if (!on) { paired = emptyList(); found = emptyList() }
        }
        return ok
    }

    fun discover() {
        val a = adapter ?: return
        seen.clear()
        found = emptyList()
        status = if (runCatching { a.startDiscovery() }.getOrDefault(false)) "Recherche d'appareils…" else "Recherche impossible (localisation ?)"
        refresh()
    }

    fun pair(address: String) {
        val dev = seen[address] ?: return
        runCatching { adapter?.cancelDiscovery() }
        status = if (dev.createBond()) "Jumelage avec ${dev.name}…" else "Échec du jumelage avec ${dev.name}"
    }

    /** Oublie un appareil jumelé (méthode cachée removeBond, accessible sous Android 7). */
    fun forget(address: String) {
        val dev = adapter?.bondedDevices?.firstOrNull { it.address == address } ?: return
        val ok = runCatching { dev.javaClass.getMethod("removeBond").invoke(dev) as Boolean }.getOrDefault(false)
        status = if (ok) "${dev.name} oublié" else "Impossible d'oublier ${dev.name}"
        refresh()
    }

    private fun BluetoothDevice.toBt(): BtDevice {
        val cls = bluetoothClass
        val major = cls?.majorDeviceClass
        val label = name?.takeIf { it.isNotBlank() } ?: address
        val remote = major == BluetoothClass.Device.Major.PERIPHERAL ||
            label.contains("remote", ignoreCase = true) || label.contains("télécommande", ignoreCase = true)
        val kind = when (major) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio"
            BluetoothClass.Device.Major.PERIPHERAL -> if (label.contains("remote", true)) "Télécommande" else "Manette / clavier"
            BluetoothClass.Device.Major.PHONE -> "Téléphone"
            BluetoothClass.Device.Major.COMPUTER -> "Ordinateur"
            else -> "Appareil"
        }
        // isConnected() est caché mais public sous Android 7 : on l'appelle par réflexion.
        val connected = runCatching { javaClass.getMethod("isConnected").invoke(this) as Boolean }.getOrDefault(false)
        return BtDevice(address, label, bondState == BluetoothDevice.BOND_BONDED, connected, remote, kind)
    }
}
