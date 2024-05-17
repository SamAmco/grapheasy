/*
 *  This file is part of Track & Graph
 *
 *  Track & Graph is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Track & Graph is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.samco.trackandgraph.ui.compose.theming

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import com.google.android.material.composethemeadapter.createMdcTheme

private val perfectWhite = Color(0xffffffff)
private val lightGray = Color(0xFFE0E0E0)
private val darkGray = Color(0xFF4C4C4C)
private val perfectBlack = Color(0xff000000)

private val warmWhite = Color(0xfffffbfa)
private val pippin = Color(0xffffe8e1)

private val darkCharcoal = Color(0xff121212)
private val midCharcoal = Color(0xff222222)
private val lightCharcoal = Color(0xff373737)

private val lightFormInputBorder = Color(0xffe8d7d0)
private val lightFormInputText = Color(0xff6e6e6e)
private val lightFormDisabledBackground = Color(0xffded1cb)
private val lightFormDisabledBorder = Color(0xffd8d8d8)

private val darkFormInputText = Color(0xff858585)
private val darkFormDisabledBackground = Color(0xff2d2e3e)
private val darkFormDisabledBorder = Color(0xff737373)

data class TngColors(
    val material: Colors,
    val selectorButtonColor: Color,
    val inputBackgroundColor: Color,
    val inputBorderColor: Color,
    val inputTextColor: Color,
    val disabledBackgroundColor: Color,
    val disabledBorderColor: Color
) {
    val primary get() = material.primary
    val primaryVariant get() = material.primaryVariant
    val secondary get() = material.secondary
    val secondaryVariant get() = material.secondaryVariant
    val background get() = material.background
    val surface get() = material.surface
    val error get() = material.error
    val onPrimary get() = material.onPrimary
    val onSecondary get() = material.onSecondary
    val onBackground get() = material.onBackground
    val onSurface get() = material.onSurface
    val onError get() = material.onError
    val isLight get() = material.isLight
    val textNoteColor get() = material.onSurface.copy(alpha = 0.5f)
}

private val LightColorPalette = TngColors(
    material = lightColors(),
    selectorButtonColor = lightGray,
    inputBackgroundColor = perfectWhite,
    inputBorderColor = lightFormInputBorder,
    inputTextColor = lightFormInputText,
    disabledBackgroundColor = lightFormDisabledBackground,
    disabledBorderColor = lightFormDisabledBorder
)

private val DarkColorPalette = TngColors(
    material = darkColors(),
    selectorButtonColor = darkGray,
    inputBackgroundColor = perfectBlack,
    inputBorderColor = lightCharcoal,
    inputTextColor = darkFormInputText,
    disabledBackgroundColor = darkFormDisabledBackground,
    disabledBorderColor = darkFormDisabledBorder
)

val TngColors.disabledAlpha get() = 0.4f

private val LocalColors = staticCompositionLocalOf { LightColorPalette }

val MaterialTheme.tngColors: TngColors
    @Composable
    @ReadOnlyComposable
    get() = LocalColors.current

@Composable
fun TnGComposeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    block: @Composable () -> Unit
) {
    val (materialColors, typography, shapes) = createMdcTheme(
        context = LocalContext.current,
        layoutDirection = LocalLayoutDirection.current
    )

    val colors = tngColors(darkTheme, materialColors)

    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(
            colors = colors.material,
            typography = typography ?: MaterialTheme.typography,
            shapes = shapes ?: MaterialTheme.shapes,
            content = block
        )
    }
}

@Composable
fun DialogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    block: @Composable () -> Unit
) = TnGComposeTheme(darkTheme) {

    val colors = MaterialTheme.tngColors.let {
        if (darkTheme) {
            it.copy(
                material = MaterialTheme.tngColors.material.copy(
                    background = midCharcoal,
                    surface = midCharcoal,
                )
            )
        } else it
    }


    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(
            colors = colors.material,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = block
        )
    }
}

@Composable
fun FormTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    block: @Composable () -> Unit
) = TnGComposeTheme(darkTheme) {
    val colors = MaterialTheme.tngColors.copy(
        material = MaterialTheme.tngColors.material.copy(
            background = if (darkTheme) darkCharcoal else warmWhite,
            surface = if (darkTheme) midCharcoal else pippin,
            onPrimary = warmWhite
        )
    )

    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(
            colors = colors.material,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = block
        )
    }
}

@Composable
private fun tngColors(
    darkTheme: Boolean,
    materialColors: Colors?
) = if (darkTheme) {
    DarkColorPalette.copy(material = materialColors ?: darkColors())
} else {
    LightColorPalette.copy(material = materialColors ?: lightColors())
}
