package com.fares.glass.gfx

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.fares.glass.data.AppEntry
import kotlin.math.max
import kotlin.math.roundToInt

class AppVisual(
    val tile: ImageBitmap,
    /** Version minuscule et floutée de la tuile : sert de « Top Shelf » coloré en plein écran. */
    val ambience: ImageBitmap,
)

/**
 * Fabrique l'image d'une tuile au format tvOS (5:3) à partir des visuels de l'app elle-même :
 * 1. la bannière Android TV (le vrai logo rectangulaire de l'app), en pleine carte ;
 * 2. sinon, un logo sur fond uni : la carte prend ce fond et le logo est posé au centre ;
 * 3. sinon (fond travaillé ou transparent) : logo de l'app et son nom sur carte blanche, comme une bannière TV.
 */
class TileFactory(context: Context) {
    private val pm: PackageManager = context.packageManager

    /** Google Sans Flex (licence OFL, fournie par Google) : la typo des logotypes Google comme Chrome. */
    private val wordmarkFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, "fonts/GoogleSansFlex-Regular.ttf")
    }.getOrDefault(Typeface.create("sans-serif", Typeface.NORMAL))


    fun build(e: AppEntry): AppVisual {
        val info = runCatching { pm.getActivityInfo(ComponentName(e.pkg, e.activity), 0) }.getOrNull()
        // ComponentInfo.loadBanner retombe sur la bannière de l'application si l'activité n'en a pas.
        val banner = info?.loadBanner(pm)
        val icon = info?.loadIcon(pm) ?: pm.defaultActivityIcon
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        when {
            banner != null -> drawBanner(canvas, banner)
            Build.VERSION.SDK_INT >= 26 && icon is AdaptiveIconDrawable -> drawAdaptive(canvas, icon, e.label)
            else -> drawLegacy(canvas, icon, e.label)
        }
        val ambience = Blur.frosted(bmp, 64, 11f, 1.6f, 0.62f)
        return AppVisual(bmp.asImageBitmap(), ambience.asImageBitmap())
    }

    /**
     * La bannière couvre toute la carte. Si son format s'éloigne trop du 16:9 (bannière presque carrée),
     * elle est affichée entière sur la couleur de son bord plutôt que rognée.
     */
    private fun drawBanner(c: Canvas, d: Drawable) {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: W
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: H
        val ratio = iw / ih.toFloat()
        val cover = ratio > 1.55f
        c.drawColor(if (cover) Color.BLACK else sample(d).edgeColor)
        val s = if (cover) max(W / iw.toFloat(), H / ih.toFloat()) else minOf(W / iw.toFloat(), H / ih.toFloat())
        val dw = (iw * s).roundToInt()
        val dh = (ih * s).roundToInt()
        val left = (W - dw) / 2
        val top = (H - dh) / 2
        d.setBounds(left, top, left + dw, top + dh)
        d.draw(c)
    }

    @androidx.annotation.RequiresApi(26)
    private fun drawAdaptive(c: Canvas, icon: AdaptiveIconDrawable, label: String) {
        val bg = icon.background
        val fg = icon.foreground
        val solid = bg?.let { uniformColor(it) }
        if (solid == null || fg == null) {
            // Fond travaillé (Chrome…) : comme une bannière Android TV, logo rond et nom sur carte blanche.
            drawWordmark(c, icon, label)
            return
        }
        // Logo sur fond uni : la carte prend ce fond, le logo reste à taille raisonnable au centre.
        c.drawColor(solid)
        val size = (H * 1.12f).roundToInt()
        val left = (W - size) / 2
        val top = (H - size) / 2
        fg.setBounds(left, top, left + size, top + size)
        fg.draw(c)
    }

    private fun drawLegacy(c: Canvas, d: Drawable, label: String) {
        val probe = sample(d)
        if (!probe.edgeOpaque) {
            drawWordmark(c, d, label)
            return
        }
        // Icône pleine (logo sur fond de couleur) : la carte prolonge ce fond, l'icône est agrandie
        // juste assez pour faire disparaître ses marges et ses coins arrondis.
        fill(c, probe.edgeColor)
        val size = (H * 1.05f).roundToInt()
        val left = (W - size) / 2
        val top = (H - size) / 2
        d.setBounds(left, top, left + size, top + size)
        d.draw(c)
    }

    /** Carte de style bannière TV : logo de l'app à gauche, son nom à droite, sur fond blanc (noir si logo clair). */
    private fun drawWordmark(c: Canvas, icon: Drawable, label: String) {
        val dark = sample(icon).logoLuminance > 0.8f
        c.drawColor(if (dark) 0xFF1C1C1E.toInt() else Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = wordmarkFont
            color = if (dark) Color.WHITE else 0xFF5F6368.toInt()
            textSize = H * 0.26f
        }
        val iconSize = H * 0.5f
        val gap = H * 0.1f
        while (iconSize + gap + paint.measureText(label) > W * 0.86f && paint.textSize > 12f) paint.textSize *= 0.94f
        val total = iconSize + gap + paint.measureText(label)
        val left = (W - total) / 2f
        val top = (H - iconSize) / 2f
        icon.setBounds(left.roundToInt(), top.roundToInt(), (left + iconSize).roundToInt(), (top + iconSize).roundToInt())
        icon.draw(c)
        val fm = paint.fontMetrics
        c.drawText(label, left + iconSize + gap, H / 2f - (fm.ascent + fm.descent) / 2f, paint)
    }

    /** Couleur du calque si elle est uniforme (fond plat), sinon null. */
    private fun uniformColor(d: Drawable): Int? {
        val n = 24
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, n, n)
        d.draw(Canvas(bmp))
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        val ref = px[n * n / 2 + n / 2]
        val close = px.count { p ->
            Color.alpha(p) > 230 &&
                kotlin.math.abs(Color.red(p) - Color.red(ref)) < 14 &&
                kotlin.math.abs(Color.green(p) - Color.green(ref)) < 14 &&
                kotlin.math.abs(Color.blue(p) - Color.blue(ref)) < 14
        }
        return if (close > px.size * 0.92f) ref else null
    }

    /** Aplat exactement de la couleur du bord de l'icône : la jonction icône / carte est invisible. */
    private fun fill(c: Canvas, color: Int) = c.drawColor(color)

    private class Probe(val edgeOpaque: Boolean, val edgeColor: Int, val logoLuminance: Float)

    /** Analyse l'icône : son pourtour est-il opaque, de quelle couleur, et le logo est-il clair ? */
    private fun sample(d: Drawable): Probe {
        val n = 96
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, n, n)
        d.draw(Canvas(bmp))
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        // Anneau à 14 % du bord : à l'intérieur des coins arrondis et des marges usuelles.
        val inset = (n * 0.14f).toInt()
        var opaque = 0
        var total = 0
        var r = 0L; var g = 0L; var b = 0L
        for (i in inset until n - inset) {
            for (p in intArrayOf(px[inset * n + i], px[(n - 1 - inset) * n + i], px[i * n + inset], px[i * n + n - 1 - inset])) {
                total++
                if (Color.alpha(p) > 230) {
                    opaque++; r += Color.red(p); g += Color.green(p); b += Color.blue(p)
                }
            }
        }
        var lum = 0f
        var count = 0
        for (p in px) if (Color.alpha(p) > 200) {
            lum += (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)) / 255f
            count++
        }
        val edgeColor = if (opaque > 0) Color.rgb((r / opaque).toInt(), (g / opaque).toInt(), (b / opaque).toInt()) else Color.WHITE
        return Probe(opaque > total * 0.6f, edgeColor, if (count > 0) lum / count else 0f)
    }

    companion object {
        /** Taille de rendu des tuiles en pixels : couvre la tuile agrandie au focus sur un écran 1080p. */
        const val W = 360
        const val H = 216
    }
}
