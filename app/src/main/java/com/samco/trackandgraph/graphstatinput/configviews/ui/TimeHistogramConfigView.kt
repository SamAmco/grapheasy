/*
* This file is part of Track & Graph
*
* Track & Graph is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Track & Graph is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.samco.trackandgraph.graphstatinput.configviews.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.TimeHistogramWindow
import com.samco.trackandgraph.graphstatinput.GraphStatConfigEvent
import com.samco.trackandgraph.graphstatinput.configviews.viewmodel.TimeHistogramConfigViewModel
import com.samco.trackandgraph.graphstatinput.customviews.GraphStatDurationSpinner
import com.samco.trackandgraph.graphstatinput.customviews.GraphStatEndingAtSpinner
import com.samco.trackandgraph.ui.compose.ui.*

@Composable
fun TimeHistogramConfigView(
    scrollState: ScrollState,
    viewModelStoreOwner: ViewModelStoreOwner,
    graphStatId: Long,
    onConfigEvent: (GraphStatConfigEvent?) -> Unit
) = Column {
    val viewModel = hiltViewModel<TimeHistogramConfigViewModel>(viewModelStoreOwner).apply {
        initFromGraphStatId(graphStatId)
    }

    LaunchedEffect(viewModel) {
        viewModel.getConfigFlow().collect { onConfigEvent(it) }
    }

    FormSection {
        Column {
            GraphStatDurationSpinner(
                modifier = Modifier,
                selectedDuration = viewModel.selectedDuration,
                onDurationSelected = { viewModel.updateDuration(it) }
            )

            FormFieldSeparator()

            GraphStatEndingAtSpinner(
                modifier = Modifier,
                sampleEndingAt = viewModel.sampleEndingAt
            ) { viewModel.updateSampleEndingAt(it) }
        }
    }

    FormFieldSeparator()

    FormLabel(text = stringResource(id = R.string.select_a_feature))

    val featureId = viewModel.featureId
    val featureMap = viewModel.featureMap

    if (featureId != null && featureMap != null) {
        FormSpinner(
            strings = featureMap,
            selectedItem = featureId,
            onItemSelected = { viewModel.updateFeatureId(it) }
        )
    }

    FormFieldSeparator()

    FormLabel(text = stringResource(id = R.string.time_window_size))

    val stringArray = stringArrayResource(id = R.array.time_histogram_windows)
    val timeWindows = remember { mapOf(
        TimeHistogramWindow.HOUR to stringArray[0],
        TimeHistogramWindow.DAY to stringArray[1],
        TimeHistogramWindow.WEEK to stringArray[2],
        TimeHistogramWindow.MONTH to stringArray[3],
        TimeHistogramWindow.THREE_MONTHS to stringArray[4],
        TimeHistogramWindow.SIX_MONTHS to stringArray[5],
        TimeHistogramWindow.YEAR to stringArray[6]
    )}

    FormSpinner(
        strings = timeWindows,
        selectedItem = viewModel.selectedWindow,
        onItemSelected = { viewModel.updateWindow(it) }
    )

    FormFieldSeparator()

    FormSwitchInput(
        checked = viewModel.sumByCount,
        onCheckedChange = { viewModel.updateSumByCount(it) },
        text = stringResource(id = R.string.sum_by_count_checkbox_label)
    )
}