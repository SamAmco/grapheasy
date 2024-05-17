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
@file:OptIn(ExperimentalComposeUiApi::class)

package com.samco.trackandgraph.graphstatinput.configviews.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.*
import com.samco.trackandgraph.graphstatinput.GraphStatConfigEvent
import com.samco.trackandgraph.graphstatinput.configviews.viewmodel.LineGraphConfigViewModel
import com.samco.trackandgraph.graphstatinput.customviews.GraphStatDurationSpinner
import com.samco.trackandgraph.graphstatinput.customviews.GraphStatEndingAtSpinner
import com.samco.trackandgraph.graphstatinput.customviews.GraphStatYRangeTypeSpinner
import com.samco.trackandgraph.graphstatinput.customviews.YRangeFromToInputs
import com.samco.trackandgraph.ui.compose.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LineGraphConfigView(
    scrollState: ScrollState,
    viewModelStoreOwner: ViewModelStoreOwner,
    graphStatId: Long,
    onConfigEvent: (GraphStatConfigEvent?) -> Unit
) {
    val viewModel = hiltViewModel<LineGraphConfigViewModel>(viewModelStoreOwner).apply {
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

            FormFieldSeparator()

            GraphStatYRangeTypeSpinner(
                yRangeType = viewModel.yRangeType,
                onYRangeTypeSelected = { viewModel.updateYRangeType(it) }
            )

            if (viewModel.yRangeType == YRangeType.FIXED) {
                FormFieldSeparator()
                YRangeFromToInputs(viewModel)
            }
        }
    }

    FormFieldSeparator()

    LineGraphFeaturesInputView(scrollState, viewModel)
}

@Composable
private fun LineGraphFeaturesInputView(
    scrollState: ScrollState,
    viewModel: LineGraphConfigViewModel
) = Column(
    modifier = Modifier
        .fillMaxWidth()
        .animateContentSize(),
    horizontalAlignment = Alignment.CenterHorizontally
) {

    for (index in viewModel.lineGraphFeatures.indices) {
        val lgf = viewModel.lineGraphFeatures[index]
        LineGraphFeatureInputView(
            lgf = lgf,
            features = viewModel.featurePaths,
            textFields = viewModel.getTextFieldsFor(index),
            onRemove = { viewModel.removeLineGraphFeature(index) },
            onUpdate = { viewModel.updateLineGraphFeature(index, it) }
        )
        SpacingSmall()
    }

    val coroutineScope = rememberCoroutineScope()

    AddBarButton(
        onClick = {
            viewModel.onAddLineGraphFeatureClicked()
            coroutineScope.launch {
                delay(200)
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        },
    )
}

@Composable
private fun LineGraphFeatureInputView(
    lgf: LineGraphFeature,
    features: List<LineGraphConfigViewModel.FeaturePathViewData>,
    textFields: LineGraphConfigViewModel.FeatureTextFields,
    onRemove: () -> Unit,
    onUpdate: (LineGraphFeature) -> Unit
) = Card {
    Column(
        modifier = Modifier.padding(dimensionResource(id = R.dimen.card_padding)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormTextInput(
                modifier = Modifier.weight(1f),
                textFieldValue = textFields.name,
                onValueChange = { textFields.updateName(it) }
            )
            IconButton(onClick = { onRemove() }) {
                Icon(
                    painter = painterResource(id = R.drawable.delete_icon),
                    contentDescription = stringResource(id = R.string.delete_line_button_content_description)
                )
            }
        }

        FormFieldSeparator()

        Row(modifier = Modifier.height(IntrinsicSize.Max)) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                ColorSpinner(
                    selectedColor = lgf.colorIndex,
                    onColorSelected = { onUpdate(lgf.copy(colorIndex = it)) }
                )

                val pointStyleIcons = listOf(
                    R.drawable.point_style_none_icon,
                    R.drawable.point_style_circles_icon,
                    R.drawable.point_style_circles_and_numbers_icon
                )

                CircleSpinner(
                    numItems = pointStyleIcons.size,
                    selectedIndex = LineGraphPointStyle.values().indexOf(lgf.pointStyle),
                    onIndexSelected = { onUpdate(lgf.copy(pointStyle = LineGraphPointStyle.values()[it])) }
                ) {
                    Icon(
                        painter = painterResource(id = pointStyleIcons[it]),
                        contentDescription = null
                    )
                }
            }
            Column {
                val hoursStr = stringResource(id = R.string.hours)
                val minutesStr = stringResource(id = R.string.minutes)
                val secondsStr = stringResource(id = R.string.seconds)

                val featureNames = remember(features) {
                    features.associateBy(
                        keySelector = { it.id },
                        valueTransform = {
                            when (it.id.durationPlottingMode) {
                                DurationPlottingMode.NONE, DurationPlottingMode.DURATION_IF_POSSIBLE -> it.path
                                DurationPlottingMode.HOURS -> "${it.path} ($hoursStr)"
                                DurationPlottingMode.MINUTES -> "${it.path} ($minutesStr)"
                                DurationPlottingMode.SECONDS -> "${it.path} ($secondsStr)"
                            }
                        }
                    )
                }

                val selectedItem = remember(lgf) {
                    LineGraphConfigViewModel.FeatureSelectionIdentifier(
                        featureId = lgf.featureId,
                        durationPlottingMode = lgf.durationPlottingMode
                    )
                }

                FormSpinner(
                    strings = featureNames,
                    selectedItem = selectedItem,
                    onItemSelected = {
                        onUpdate(
                            lgf.copy(
                                featureId = it.featureId,
                                durationPlottingMode = it.durationPlottingMode
                            )
                        )
                    }
                )

                FormFieldSeparator()

                val averagingModeNames =
                    stringArrayResource(id = R.array.line_graph_averaging_mode_names)
                        .mapIndexed { index, name -> index to name }
                        .associate { (index, name) -> LineGraphAveraginModes.values()[index] to name }

                FormSpinner(
                    strings = averagingModeNames,
                    selectedItem = lgf.averagingMode,
                    onItemSelected = { onUpdate(lgf.copy(averagingMode = it)) }
                )

                FormFieldSeparator()

                val plotModeNames = stringArrayResource(id = R.array.line_graph_plot_mode_names)
                    .mapIndexed { index, name -> index to name }
                    .associate { (index, name) -> LineGraphPlottingModes.values()[index] to name }

                FormSpinner(
                    strings = plotModeNames,
                    selectedItem = lgf.plottingMode,
                    onItemSelected = { onUpdate(lgf.copy(plottingMode = it)) }
                )

                FormFieldSeparator()

                FormTwoPartsTextInput(
                    firstFieldValue = textFields.offset,
                    secondFieldValue = textFields.scale,
                    onFirstValueChange = { textFields.updateOffset(it) },
                    onSecondValueChange = { textFields.updateScale(it) },
                    firstLabel = stringResource(id = R.string.offset),
                    secondLabel = stringResource(id = R.string.scale),
                    isNumeric = true
                )
            }
        }
    }
}