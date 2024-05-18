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

package com.samco.trackandgraph.addgroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.theming.DialogTheme
import com.samco.trackandgraph.ui.compose.ui.ColorSpinner
import com.samco.trackandgraph.ui.compose.ui.CustomConfirmCancelDialog
import com.samco.trackandgraph.ui.compose.ui.SpacingLarge

@Composable
fun AddGroupDialog(viewModel: AddGroupDialogViewModel, onDismissRequest: () -> Unit = {}) {
    if (!viewModel.hidden) {
        DialogTheme {

            val addEnabled = viewModel.addEnabled.collectAsStateWithLifecycle().value
            val updateMode = viewModel.updateMode.collectAsStateWithLifecycle().value

            CustomConfirmCancelDialog(
                onDismissRequest = { onDismissRequest() },
                onConfirm = {
                    viewModel.addOrUpdateGroup()
                    onDismissRequest()
                },
                customWidthPercentage = 0.9f,
                continueText = if (updateMode) R.string.update else R.string.add,
                dismissText = R.string.cancel,
                continueEnabled = addEnabled,
            ) {
                AddGroupView(
                    modifier = Modifier.fillMaxWidth(),
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun AddGroupView(modifier: Modifier, viewModel: AddGroupDialogViewModel) =
    Column(modifier) {
        Text(
            text = stringResource(id = R.string.add_group),
            style = MaterialTheme.typography.h6
        )

        SpacingLarge()

        val focusRequester = remember { FocusRequester() }

        Row(modifier = Modifier.fillMaxWidth()) {
            ColorSpinner(
                selectedColor = viewModel.colorIndex,
                onColorSelected = viewModel::updateColorIndex,
            )

            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                value = viewModel.name,
                onValueChange = viewModel::updateName
            )
        }

        LaunchedEffect(viewModel.hidden) {
            if (!viewModel.hidden) focusRequester.requestFocus()
        }
    }