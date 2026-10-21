package com.fares.glass.sys

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Raccourcis vers les écrans de réglages du système, valables sur n'importe quelle Android TV.
 * Pour chaque section on essaie plusieurs écrans (Fire OS, Android TV, Google TV, AOSP) et on garde
 * le premier qu'une app tierce a vraiment le droit d'ouvrir : exporté, sans permission réservée
 * (Fire OS protège par exemple « Affichage et sons » par une permission signature|amazon), et différent
 * de l'accueil des réglages. Une section sans écran utilisable n'est pas proposée.
 */
class SettingsShortcuts(context: Context) {
    private val app = context.applicationContext
    private val pm = app.packageManager

    enum class Section(val title: String, vararg val actions: String) {
        Display("Affichage et sons", "com.amazon.device.settings.action.DISPLAY_AND_SOUNDS", Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SOUND_SETTINGS),
        Devices("Appareils", "android.settings.CONTROLLERS_SETTINGS", Settings.ACTION_BLUETOOTH_SETTINGS),
        Apps("Applications", Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        Accessibility("Accessibilité", Settings.ACTION_ACCESSIBILITY_SETTINGS, "com.amazon.device.settings.action.ACCESSIBILITY"),
        About("À propos", "com.amazon.device.settings.action.DEVICE", Settings.ACTION_DEVICE_INFO_SETTINGS),
        Network("Réseau", Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS),
        All("Tous les réglages", Settings.ACTION_SETTINGS),
    }

    private val home: ComponentName? by lazy { component(Intent(Settings.ACTION_SETTINGS)) }
    private val cache = HashMap<Section, Intent?>()

    /** L'écran à ouvrir pour [section], ou null si cette TV n'en propose aucun d'accessible. */
    fun intent(section: Section): Intent? = cache.getOrPut(section) {
        section.actions.asSequence()
            .map { Intent(it) }
            .firstOrNull { candidate ->
                val target = usable(candidate) ?: return@firstOrNull false
                section == Section.All || target != home
            }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun available(section: Section) = intent(section) != null

    private fun component(intent: Intent): ComponentName? =
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let { ComponentName(it.packageName, it.name) }

    /** Composant qui recevra l'intent si Glass a le droit de l'ouvrir, sinon null. */
    private fun usable(intent: Intent): ComponentName? {
        val info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return null
        if (!info.exported) return null
        val needed = info.permission
        if (needed != null && app.checkSelfPermission(needed) != PackageManager.PERMISSION_GRANTED) return null
        return ComponentName(info.packageName, info.name)
    }
}
