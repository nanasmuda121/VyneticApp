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

/** Dark iPhone-inspired liquid-glass panel on a soft-white canvas. */
fun Modifier.vyneticGlass(shape: Shape = RoundedCornerShape(22.dp)): Modifier =
    this.clip(shape)
        .background(
            Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xE62A2D33),
                    0.24f to Color(0xD91B1E24),
                    0.62f to Color(0xF20B0D11),
                    1.0f to Color(0xE626292F)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x99FFFFFF),
                    Color(0x668D939D),
                    Color(0x22FFFFFF),
                    Color(0x88777D87)
                )
            ),
            shape = shape
        )
