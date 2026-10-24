package com.fares.glass

import android.view.KeyEvent
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import com.fares.glass.data.AppEntry
import com.fares.glass.data.AppRepository
import com.fares.glass.gfx.Blur
import com.fares.glass.gfx.Wallpapers
import com.fares.glass.ui.Backdrop
import com.fares.glass.ui.Command
import com.fares.glass.ui.HomeScreen
import com.fares.glass.ui.HomeState
import com.fares.glass.ui.Typo
import com.fares.glass.ui.CcPage
import com.fares.glass.ui.ControlCenter
import com.fares.glass.ui.Mode
import com.fares.glass.sys.BluetoothController
import com.fares.glass.sys.SystemInfo
import com.fares.glass.sys.SettingsShortcuts
import androidx.activity.result.contract.ActivityResultContracts
import com.fares.glass.sys.WifiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private val home = HomeState()
    private val backdrop = Backdrop()
    private val wallpaper = mutableStateOf<ImageBitmap?>(null)
    private val wallpaperName = mutableStateOf("")
    private lateinit var cc: ControlCenter
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cc.onPermissionsResult()
    }

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let(repo::invalidate)
            lifecycleScope.launch { repo.refresh() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        Typo.load(this)
        intent.getStringExtra("name")?.let(repo::setName)

        cc = ControlCenter(
            this, repo, WifiController(this), BluetoothController(this), SystemInfo(this), SettingsShortcuts(this),
            onOpen = ::startSafely,
            onRequestPermissions = { permissions.launch(it) },
            onNextWallpaper = { run(Command.NextWallpaper) },
            onClose = { home.mode = Mode.Browse },
        )
        onBackPressedDispatcher.addCallback(this) {}
        registerReceiver(packages, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        setContent {
            HomeScreen(
                repo, home, backdrop, wallpaper.value, wallpaperName.value, cc,
                onLaunch = ::launch, onCommand = ::run,
            )
        }
        loadWallpaper()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { repo.refresh() }
    }

    override fun onPause() {
        super.onPause()
        if (home.mode == Mode.Control) cc.close()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("name")?.let(repo::setName)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) home.goHome()
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_SETTINGS) {
                if (home.mode == Mode.Control) cc.close() else cc.open(CcPage.Main)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun loadWallpaper() {
        val index = 0
        lifecycleScope.launch {
            val (sharp, frosted) = withContext(Dispatchers.Default) {
                val src = Wallpapers.load(this@MainActivity, index)
                src to Blur.frosted(src, 480, 10f, 1.45f, 1.0f)
            }
            wallpaper.value = sharp.asImageBitmap()
            backdrop.frosted = frosted.asImageBitmap()
            wallpaperName.value = Wallpapers.name(this@MainActivity, index)
        }
    }

    private fun launch(entry: AppEntry, bounds: Rect?) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(entry.packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opts = bounds?.let { ActivityOptions.makeScaleUpAnimation(window.decorView, it.left, it.top, it.width(), it.height()) }
            startActivity(intent, opts?.toBundle())
        } catch (e: Exception) {
            Log.e("Glass", "Failed to launch ${entry.packageName}", e)
        }
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("Glass", "Failed to start intent", e)
        }
    }

    private fun run(cmd: Command) {
        when (cmd) {
            is Command.NextWallpaper -> loadWallpaper()
        }
    }
}
