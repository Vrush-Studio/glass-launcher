package com.fares.glass.sys

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import java.util.Locale

/** Mémoire, stockage, version et vitesse des animations (WRITE_SECURE_SETTINGS accordée par ADB). */
class SystemInfo(context: Context) {
    private val app = context.applicationContext
    private val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    class Usage(val usedFraction: Float, val label: String, val free: String)

    fun memory(): Usage {
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val used = 1f - info.availMem.toFloat() / info.totalMem
        return Usage(used, "${mb(info.availMem)} libres sur ${mb(info.totalMem)}", "${mb(info.availMem)} libres")
    }

    fun storage(): Usage {
        val fs = StatFs(Environment.getDataDirectory().path)
        val total = fs.blockCountLong * fs.blockSizeLong
        val free = fs.availableBlocksLong * fs.blockSizeLong
        return Usage(1f - free.toFloat() / total, "${gb(free)} libres sur ${gb(total)}", "${gb(free)} libres")
    }

    val osVersion: String by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, "ro.build.version.name") as String
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Android ${android.os.Build.VERSION.RELEASE}"
    }

    /** Vitesse d'animation actuelle : 1, 0,5 ou 0. */
    fun animationScale(): Float = Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    fun missing(permission: String) = app.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED

    fun canWriteSettings() =
        app.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Passe à la vitesse suivante (1× → 0,5× → coupées → 1×). Retourne false sans la permission. */
    fun cycleAnimations(): Boolean {
        if (!canWriteSettings()) return false
        val next = when (animationScale()) {
            1f -> 0.5f
            0.5f -> 0f
            else -> 1f
        }
        val cr = app.contentResolver
        return runCatching {
            Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, next)
            Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, next)
            Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, next)
        }.isSuccess
    }

    private fun mb(bytes: Long) = "${bytes / (1024 * 1024)} Mo"
    private fun gb(bytes: Long) = String.format(Locale.FRANCE, "%.1f Go", bytes / 1_073_741_824.0)
}
