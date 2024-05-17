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
package com.samco.trackandgraph.graphstatinput.customviews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.YRangeType
import com.samco.trackandgraph.ui.compose.ui.FormLabel
import com.samco.trackandgraph.ui.compose.ui.FormSpinner

@Composable
fun GraphStatYRangeTypeSpinner(
    yRangeType: YRangeType,
    onYRangeTypeSelected: (YRangeType) -> Unit
) {
    FormLabel(text = stringResource(id = R.string.range_style))

    val strings = stringArrayResource(id = R.array.y_range_styles)

    val spinnerItems = remember { mapOf(
        YRangeType.DYNAMIC to strings[0],
        YRangeType.FIXED to strings[1]
    )}

    FormSpinner(
        strings = spinnerItems,
        selectedItem = yRangeType,
        onItemSelected = onYRangeTypeSelected
    )
}