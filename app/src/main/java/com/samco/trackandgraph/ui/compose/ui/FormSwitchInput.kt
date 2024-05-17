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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.theming.tngColors

@Composable
fun FormSwitchInput(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    text: String,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onCheckedChange?.invoke(!checked) }
        .padding(
            0.dp,
            dimensionResource(id = R.dimen.form_label_padding_bottom),
            0.dp,
            dimensionResource(id = R.dimen.form_label_padding_bottom)
        )
) {
    Text(
        text = text,
        modifier = Modifier.weight(1f)
    )

    Switch(
        modifier = Modifier
            .height(25.dp),
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.tngColors.primary,
            checkedTrackColor = MaterialTheme.tngColors.primary,
            checkedTrackAlpha = 0.2F,
            uncheckedThumbColor = MaterialTheme.tngColors.disabledBorderColor,
            uncheckedTrackColor = MaterialTheme.tngColors.disabledBorderColor,
            uncheckedTrackAlpha = 0.2F,
            disabledCheckedThumbColor = MaterialTheme.tngColors.disabledBackgroundColor,
            disabledCheckedTrackColor = MaterialTheme.tngColors.disabledBackgroundColor,
            disabledUncheckedThumbColor = MaterialTheme.tngColors.disabledBackgroundColor,
            disabledUncheckedTrackColor = MaterialTheme.tngColors.disabledBackgroundColor
        )
    )
}
