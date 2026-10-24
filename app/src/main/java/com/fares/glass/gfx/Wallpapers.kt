package com.fares.glass.gfx

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Environment
import androidx.annotation.StringRes
import com.fares.glass.R
import java.io.File
import kotlin.random.Random

object Wallpapers {

    private class Preset(@StringRes val name: Int, val base: Int, val blobs: IntArray, val ribbons: IntArray)

    private val presets = listOf(
        Preset(R.string.wallpaper_liquid, 0xFF070B1E.toInt(),
            intArrayOf(0xFF3D2BE0.toInt(), 0xFFB0309A.toInt(), 0xFF0E9FB8.toInt(), 0xFF1B3FA8.toInt(), 0xFFFF7A45.toInt()),
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF9AD8FF.toInt(), 0xFFFFB3E6.toInt())),
    )

    private val customFile get() = File(Environment.getExternalStorageDirectory(), "Glass/wallpaper.jpg")

    private fun hasCustom(context: Context) = customFile.canRead()

    fun count(context: Context) = 1 + if (hasCustom(context)) 1 else 0

    fun name(context: Context, index: Int): String =
        if (index == 0) "Custom Wallpaper" else context.getString(R.string.wallpaper_liquid)

    fun load(context: Context, index: Int): Bitmap {
        if (customFile.canRead()) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(customFile.path, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= 1920) sample *= 2
            BitmapFactory.decodeFile(customFile.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.let { return it }
        }
        return render(presets[0], index)
    }

    private fun render(p: Preset, seed: Int, w: Int = 1280, h: Int = 720): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(p.base)
        return bmp
    }
}
