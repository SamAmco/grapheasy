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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.theming.tngColors

@Composable
fun FormSaveButton(
    isInUpdateMode: Boolean,
    isInErrorState: Boolean,
    onCreateUpdateClicked: () -> Unit
) {
    val text = if (isInUpdateMode) stringResource(id = R.string.update)
               else stringResource(id = R.string.create)

    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp, dimensionResource(id = R.dimen.form_button_margin_top), 0.dp, 0.dp),
        onClick = onCreateUpdateClicked,
        enabled = !isInErrorState,
        shape = CircleShape
    ) {
        Text(
            modifier = Modifier.padding(dimensionResource(id = R.dimen.form_button_padding)),
            text = text,
            style = MaterialTheme.typography.body1,
            fontWeight = MaterialTheme.typography.button.fontWeight,
            color = MaterialTheme.tngColors.onPrimary
        )
    }
}

