package com.fares.glass.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/** Grille tvOS de 6 colonnes sur un écran de 960×540 dp (1080p à densité 2). */
object Metrics {
    const val COLS = 6
    val Margin = 48.dp
    val Gap = 22.dp
    val TileW = 126.dp // (960 - 2 × 48 - 5 × 22) / 6
    val TileH = 76.dp // 5:3, comme les icônes tvOS
    val Pitch = 128.dp
    /** Position de la première rangée quand on est en haut : le Top Shelf occupe l'espace au-dessus. */
    val TopRowY = 392.dp
    /** Position de la rangée sélectionnée une fois qu'on est descendu dans la grille. */
    val GridRowY = 150.dp
    const val FOCUS_SCALE = 1.13f
}

enum class Mode { Browse, Top, Jiggle, Menu, Control }

/** Dernier déplacement demandé : la tuile s'incline dans cette direction (ou « bute » si bloqué). */
data class Nudge(val dx: Int, val dy: Int, val tick: Int)

class HomeState {
    var focus by mutableIntStateOf(0)
    var mode by mutableStateOf(Mode.Browse)
    var menuIndex by mutableIntStateOf(0)
    var controlIndex by mutableIntStateOf(0)
    var topIndex by mutableIntStateOf(0)
    var nudge by mutableStateOf(Nudge(0, 0, 0))
    /** OK enfoncé sur une tuile : elle « s'enfonce » avant que l'app s'ouvre. */
    var pressed by mutableStateOf(false)

    // Appui long sur OK (pas d'état Compose : ne déclenche aucun redessin).
    var centerDownAt = -1L
    var longFired = false

    val row get() = focus / Metrics.COLS

    /** Bouton Home alors qu'on est déjà sur l'accueil : retour en haut, comme sur tvOS. */
    fun goHome() {
        mode = Mode.Browse
        focus %= Metrics.COLS
    }

    fun bump(dx: Int, dy: Int) {
        nudge = Nudge(dx, dy, nudge.tick + 1)
    }

    companion object {
        /** Case visée par un déplacement, ou null si on bute contre un bord. */
        fun target(from: Int, dx: Int, dy: Int, count: Int): Int? {
            val c = Metrics.COLS
            val col = from % c
            val row = from / c
            return when {
                dx < 0 -> if (col > 0) from - 1 else null
                dx > 0 -> if (col < c - 1 && from + 1 < count) from + 1 else null
                dy < 0 -> if (row > 0) from - c else null
                dy > 0 -> when {
                    from + c < count -> from + c
                    row < (count - 1) / c -> count - 1
                    else -> null
                }
                else -> null
            }
        }
    }
}
