package com.fares.glass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.Image
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ControlItem(val icon: ImageVector, val title: String, val subtitle: String?)

fun menuItems() = listOf(
    ControlItem(Icons.Rounded.OpenWith, "Déplacer", null),
    ControlItem(Icons.Rounded.VisibilityOff, "Masquer", null),
    ControlItem(Icons.Rounded.Info, "Infos sur l'app", null),
)

/** Style de texte dans la police de l'interface (Inter, ou celle fournie dans /sdcard/Glass/fonts). */
fun txt(color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = Typo.family)

private val white = Color.White
private val ink = Color(0xFF111114)

/** Bouton tvOS : translucide au repos, plateau blanc lumineux avec texte sombre au focus. */
@Composable
private fun GlassButton(item: ControlItem, focused: Boolean, modifier: Modifier) {
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 600f), label = "btn")
    val fg = lerpColor(white, ink, f)
    Row(
        modifier
            .graphicsLayer { scaleX = 1f + 0.05f * f; scaleY = 1f + 0.05f * f }
            .background(lerpColor(white.copy(alpha = 0.10f), white.copy(alpha = 0.94f), f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(rememberVectorPainter(item.icon), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(fg))
        Spacer(Modifier.width(10.dp))
        Column {
            BasicText(item.title, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1)
            item.subtitle?.let {
                BasicText(it, style = txt(fg.copy(alpha = 0.65f), 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ContextMenu(backdrop: Backdrop, title: String, items: List<ControlItem>, focus: Int, modifier: Modifier) {
    Glass(backdrop, modifier.width(260.dp), RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(12.dp)) {
            BasicText(
                title, Modifier.padding(start = 10.dp, top = 4.dp, bottom = 8.dp),
                style = txt(white.copy(alpha = 0.7f), 13.sp, FontWeight.SemiBold), maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            items.forEachIndexed { i, item ->
                GlassButton(item, i == focus, Modifier.fillMaxWidth())
                if (i < items.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun HintPill(backdrop: Backdrop, text: String, modifier: Modifier) {
    Glass(backdrop, modifier, RoundedCornerShape(50)) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 10.dp)) {
            BasicText(text, style = txt(white, 14.sp, FontWeight.Medium))
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** Heure courante, rafraîchie chaque seconde tant que l'élément est affiché. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

private val headerShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(0f, 3f), 18f)

/** En-tête de l'accueil : salutation, grande heure et date, comme sur l'écran de référence. */
@Composable
fun HomeHeader(name: String, modifier: Modifier) {
    val now = rememberNow()
    val minute = now / 60000
    val hour = remember(minute) { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = (if (hour in 5..17) "Bonjour" else "Bonsoir") + if (name.isBlank()) "" else " $name"
    val time = remember(minute) { SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(now)) }
    val date = remember(minute) {
        SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date(now)).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    }
    Column(modifier) {
        BasicText(greeting, style = txt(white, 17.sp, FontWeight.SemiBold).copy(shadow = headerShadow))
        BasicText(time, style = txt(white, 64.sp, FontWeight.Bold).copy(shadow = headerShadow, letterSpacing = (-1).sp))
        BasicText(date, style = txt(white.copy(alpha = 0.9f), 18.sp, FontWeight.Medium).copy(shadow = headerShadow))
    }
}

fun topItems() = listOf(
    ControlItem(Icons.Rounded.Wifi, "Wi-Fi", null),
    ControlItem(Icons.Rounded.Bluetooth, "Bluetooth", null),
    ControlItem(Icons.Rounded.Settings, "Réglages", null),
)

/** Boutons en haut à droite : pastilles Liquid Glass, plateau blanc au focus. */
@Composable
fun TopBar(backdrop: Backdrop, items: List<ControlItem>, focus: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { i, item ->
            val f by animateFloatAsState(if (i == focus) 1f else 0f, spring(stiffness = 600f), label = "top")
            val fg = lerpColor(white, ink, f)
            Glass(
                backdrop,
                Modifier.graphicsLayer { scaleX = 1f + 0.08f * f; scaleY = 1f + 0.08f * f },
                RoundedCornerShape(50),
                tint = Color.White.copy(alpha = 0.10f),
            ) {
                Row(
                    Modifier
                        .background(white.copy(alpha = 0.94f * f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(rememberVectorPainter(item.icon), null, Modifier.size(17.dp), colorFilter = ColorFilter.tint(fg))
                    Spacer(Modifier.width(7.dp))
                    BasicText(item.title, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1)
                }
            }
        }
    }
}
