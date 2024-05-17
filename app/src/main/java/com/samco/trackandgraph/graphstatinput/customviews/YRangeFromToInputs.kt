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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import com.samco.trackandgraph.R
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.YRangeConfigBehaviour
import com.samco.trackandgraph.ui.compose.ui.FormDurationInput
import com.samco.trackandgraph.ui.compose.ui.FormFieldSeparator
import com.samco.trackandgraph.ui.compose.ui.FormLabel
import com.samco.trackandgraph.ui.compose.ui.FormTextInput
import com.samco.trackandgraph.ui.compose.ui.FormTwoPartsTextInput

@Composable
fun YRangeFromToInputs(
    viewModel: YRangeConfigBehaviour,
    fromEnabled: Boolean = true
) {
    val toText =
        if (fromEnabled) stringResource(id = R.string.to)
        else stringResource(id = R.string.y_range_max)
    val timeBased = viewModel.timeBasedRange.collectAsState(initial = false).value
    if (timeBased) TimeBasedRangeInputs(viewModel, fromEnabled, toText)
    else NumericRangeInputs(viewModel, fromEnabled, toText)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TimeBasedRangeInputs(
    viewModel: YRangeConfigBehaviour,
    fromEnabled: Boolean,
    toText: String
) {
    if (fromEnabled) {
        FormLabel(text = stringResource(id = R.string.from))
        FormDurationInput(
            hoursFieldValue = viewModel.yRangeFromDurationViewModel.hours,
            minutesFieldValue = viewModel.yRangeFromDurationViewModel.minutes,
            secondsFieldValue = viewModel.yRangeFromDurationViewModel.seconds,
            onHoursValueChange = viewModel.yRangeFromDurationViewModel::setHoursText,
            onMinutesValueChange = viewModel.yRangeFromDurationViewModel::setMinutesText,
            onSecondsValueChange = viewModel.yRangeFromDurationViewModel::setSecondsText
        )
        FormFieldSeparator()
    }

    FormLabel(text = toText)
    FormDurationInput(
        hoursFieldValue = viewModel.yRangeToDurationViewModel.hours,
        minutesFieldValue = viewModel.yRangeToDurationViewModel.minutes,
        secondsFieldValue = viewModel.yRangeToDurationViewModel.seconds,
        onHoursValueChange = viewModel.yRangeToDurationViewModel::setHoursText,
        onMinutesValueChange = viewModel.yRangeToDurationViewModel::setMinutesText,
        onSecondsValueChange = viewModel.yRangeToDurationViewModel::setSecondsText
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NumericRangeInputs(
    viewModel: YRangeConfigBehaviour,
    fromEnabled: Boolean,
    toText: String
) {
    if (fromEnabled) {
        FormTwoPartsTextInput(
            firstFieldValue = viewModel.yRangeFrom,
            secondFieldValue = viewModel.yRangeTo,
            onFirstValueChange = { viewModel.updateYRangeFrom(it) },
            onSecondValueChange = { viewModel.updateYRangeTo(it) },
            firstLabel = stringResource(id = R.string.from),
            secondLabel = toText,
            isNumeric = true
        )
    } else {
        FormLabel(text = toText)
        FormTextInput(
            textFieldValue = viewModel.yRangeTo,
            onValueChange = { viewModel.updateYRangeTo(it) },
            isNumeric = true
        )
    }
}