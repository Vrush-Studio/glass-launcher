package com.fares.glass.gfx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.fares.glass.R
import java.io.File

object Wallpapers {

    private fun getWallpaperFile(context: Context): File {
        return File(context.filesDir, "wallpaper.jpg")
    }

    fun count(context: Context) = 1

    fun name(context: Context, index: Int): String = "Custom Wallpaper"

    fun load(context: Context, index: Int): Bitmap {
        val file = getWallpaperFile(context)
        if (file.exists() && file.canRead()) {
            BitmapFactory.decodeFile(file.path)?.let { return it }
        }
        // Fallback gambar hitam jika belum ada
        return Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF070B1E.toInt())
        }
    }
}
