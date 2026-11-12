package com.vynetic.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Ultra-modern dark liquid-glass panel with subtle frosted border highlights. */
fun Modifier.vyneticGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    active: Boolean = false
): Modifier =
    this.clip(shape)
        .background(
            if (active) {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xF0182438),
                        Color(0xE6131D2E),
                        Color(0xF00D1522)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xDD141A24),
                        Color(0xD00E131C),
                        Color(0xDD111620)
                    )
                )
            }
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = if (active) {
                    listOf(
                        Color(0x9938BDF8),
                        Color(0x33818CF8),
                        Color(0x2238BDF8),
                        Color(0x6638BDF8)
                    )
                } else {
                    listOf(
                        Color(0x26FFFFFF),
                        Color(0x0DFFFFFF),
                        Color(0x18FFFFFF),
                        Color(0x08FFFFFF)
                    )
                }
            ),
            shape = shape
        )
