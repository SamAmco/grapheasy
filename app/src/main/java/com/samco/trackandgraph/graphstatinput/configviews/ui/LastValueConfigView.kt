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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.samco.trackandgraph.R
import com.samco.trackandgraph.graphstatinput.GraphStatConfigEvent
import com.samco.trackandgraph.graphstatinput.configviews.viewmodel.LastValueConfigViewModel
import com.samco.trackandgraph.graphstatinput.customviews.FilterByLabelSection
import com.samco.trackandgraph.graphstatinput.customviews.FilterByValueSection
import com.samco.trackandgraph.graphstatinput.customviews.GraphStatEndingAtSpinner
import com.samco.trackandgraph.ui.compose.ui.FormFieldSeparator
import com.samco.trackandgraph.ui.compose.ui.FormLabel
import com.samco.trackandgraph.ui.compose.ui.FormSection
import com.samco.trackandgraph.ui.compose.ui.FormSpinner

@Composable
fun LastValueConfigView(
    scrollState: ScrollState,
    viewModelStoreOwner: ViewModelStoreOwner,
    graphStatId: Long,
    onConfigEvent: (GraphStatConfigEvent?) -> Unit
) {
    val viewModel = hiltViewModel<LastValueConfigViewModel>(viewModelStoreOwner).apply {
        initFromGraphStatId(graphStatId)
    }

    LaunchedEffect(viewModel) {
        viewModel.getConfigFlow().collect { onConfigEvent(it) }
    }

    FormSection {
        Column {
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

    FilterByLabelSection(viewModel)

    FormFieldSeparator()

    FilterByValueSection(viewModel)
}