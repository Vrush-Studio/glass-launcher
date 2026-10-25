package com.fares.glass.gfx

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.fares.glass.R
import java.io.File

object Wallpapers {

    private val customFile get() = File(Environment.getExternalStorageDirectory(), "Glass/wallpaper.jpg")

    private fun hasCustom(context: Context) = customFile.canRead()

    fun count(context: Context) = 1 + if (hasCustom(context)) 1 else 0

    fun name(context: Context, index: Int): String =
        if (index == 0 && hasCustom(context)) "Custom Wallpaper" else "Liquid Glass"

    fun load(context: Context, index: Int): Bitmap {
        if (hasCustom(context)) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(customFile.path, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= 1920) sample *= 2
            BitmapFactory.decodeFile(customFile.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.let { return it }
        }
        return Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF070B1E.toInt())
        }
    }
}
