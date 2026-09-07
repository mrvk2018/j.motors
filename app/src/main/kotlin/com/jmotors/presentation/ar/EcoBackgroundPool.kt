package com.jmotors.presentation.ar

import com.jmotors.R

/**
 * Session-scoped pool of bright Solarpunk / premium sci-fi 4K backdrops.
 * One drawable is picked at showroom start and kept for both SBS eyes.
 */
object EcoBackgroundPool {

    val ids: List<Int> = listOf(
        R.drawable.bg_solarpunk_canopy,
        R.drawable.bg_solarpunk_plaza,
        R.drawable.bg_solarpunk_harbor,
        R.drawable.bg_solarpunk_atrium,
    )

    fun pickSessionBackground(): Int = ids.random()
}
