package com.yourpackage.emu.chip8

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

val NeonGreen = Color(0xFF00FF88)
val NeonBlue  = Color(0xFF00CCFF)
val DarkBg    = Color(0xFF070D12)
val PixelOff  = Color(0xFF0A1510)
val GlowGreen = Color(0x2200FF88)

@Composable
fun Chip8Display(
    pixels: BooleanArray,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        val pw  = size.width / 64f
        val ph  = size.height / 32f
        val gap = minOf(pw, ph) * 0.07f

        for (row in 0 until 32) {
            for (col in 0 until 64) {
                val idx   = row * 64 + col
                val left  = col * pw + gap
                val top   = row * ph + gap
                val w     = pw - gap * 2
                val h     = ph - gap * 2

                if (pixels[idx]) {
                    drawRoundRect(
                        color       = GlowGreen,
                        topLeft     = Offset(left - 3f, top - 3f),
                        size        = Size(w + 6f, h + 6f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color       = NeonGreen,
                        topLeft     = Offset(left, top),
                        size        = Size(w, h),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                } else {
                    drawRoundRect(
                        color       = PixelOff,
                        topLeft     = Offset(left, top),
                        size        = Size(w, h),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }
}