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
package com.samco.trackandgraph.ui.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.theming.tngColors

@Composable
fun FormInputDecoration(
    modifier: Modifier = Modifier,
    isFocused: Boolean,
    content: @Composable () -> Unit
) {
    val boxShape = RoundedCornerShape(dimensionResource(id = R.dimen.form_input_corner))
    val borderStroke = if (isFocused) R.dimen.form_input_border_focus else R.dimen.form_input_border
    val borderColor = if (isFocused) MaterialTheme.tngColors.primary else MaterialTheme.tngColors.inputBorderColor

    Box(modifier = modifier
        .background(
            MaterialTheme.tngColors.inputBackgroundColor,
            shape = boxShape
        )
        .border(
            dimensionResource(id = borderStroke),
            borderColor,
            shape = boxShape
        )
        .padding(dimensionResource(id = R.dimen.form_input_padding))
    ) {
        content()
    }
}