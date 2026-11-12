package com.vynetic.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Authentic Liquid Glass design in a grey-black (abu-abu hitam) monochrome palette.
 * Combines translucent dark graphite glass, multi-layer liquid specular highlights,
 * and glossy bevel borders.
 */
fun Modifier.vyneticGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    active: Boolean = false
): Modifier =
    this.clip(shape)
        .background(
            if (active) {
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xF22A2F38),
                        0.28f to Color(0xEA1E222A),
                        0.72f to Color(0xF215171D),
                        1.0f to Color(0xF60F1115)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            } else {
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xE01F232B),
                        0.32f to Color(0xD4171920),
                        0.75f to Color(0xDE101216),
                        1.0f to Color(0xEA0B0C0F)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            }
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = if (active) {
                    listOf(
                        Color(0xB3FFFFFF), // Strong top-left liquid specular shine
                        Color(0x4DCBD5E1),
                        Color(0x1AFFFFFF),
                        Color(0x5994A3B8)  // Ambient bottom bounce reflection
                    )
                } else {
                    listOf(
                        Color(0x4DFFFFFF), // Soft top specular light
                        Color(0x1F94A3B8),
                        Color(0x0AFFFFFF),
                        Color(0x2B64748B)  // Dark smoke edge
                    )
                },
                start = Offset(0f, 0f),
                end = Offset(1000f, 1000f)
            ),
            shape = shape
        )
