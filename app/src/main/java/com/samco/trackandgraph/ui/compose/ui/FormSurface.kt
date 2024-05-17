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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.theming.tngColors

@Composable
fun FormSurface(
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.tngColors.background,
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(state = scrollState)
    ) {
        Box(
            modifier = Modifier
                .padding(dimensionResource(id = R.dimen.form_padding))
        ) {
            content()
        }
    }
}
