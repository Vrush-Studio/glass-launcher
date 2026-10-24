package com.fares.glass.gfx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.fares.glass.R
import java.io.File

object Wallpapers {
    private val customFile get() = File(Environment.getExternalStorageDirectory(), "Glass/wallpaper.jpg")

    fun count(context: Context) = 1

    fun name(context: Context, index: Int) = "Custom Wallpaper"

    fun load(context: Context, index: Int): Bitmap {
        if (customFile.canRead()) {
            BitmapFactory.decodeFile(customFile.path)?.let { return it }
        }
        return Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF070B1E.toInt())
        }
    }
}
