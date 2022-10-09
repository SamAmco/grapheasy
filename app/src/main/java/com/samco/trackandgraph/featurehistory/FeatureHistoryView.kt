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
package com.samco.trackandgraph.featurehistory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.DataPoint
import com.samco.trackandgraph.base.helpers.formatDayMonthYearHourMinuteWeekDayTwoLines
import com.samco.trackandgraph.base.helpers.getWeekDayNames
import com.samco.trackandgraph.ui.compose.theming.disabledAlpha
import com.samco.trackandgraph.ui.compose.ui.*
import org.intellij.lang.annotations.JdkConstants.HorizontalAlignment

/*
@Composable
@Preview(showBackground = true, device = Devices.PIXEL_3)
fun FeatureHistoryViewPreview() {
    TnGComposeTheme {
        FeatureHistoryView(viewModel = object : FeatureHistoryViewModel {
            override val isDuration = MutableLiveData(false)
            override val isTracker = MutableLiveData(false)
            override val dataPoints = MutableLiveData(emptyList<DataPoint>())
            override val showFeatureInfo = MutableLiveData<Feature?>(null)
            override val showDataPointInfo = MutableLiveData<DataPoint?>(null)

            override fun deleteDataPoint() {}
            override fun onEditClicked(dataPoint: DataPoint) {}
            override fun onDeleteClicked(dataPoint: DataPoint) {}
            override fun onDeleteConfirmed() { }

            override fun onDeleteDismissed() { }

            override fun onDataPointClicked(dataPoint: DataPoint) {}
            override fun onDismissDataPoint() {}

            override fun onShowFeatureInfo() {}
            override fun onHideFeatureInfo() {}
        })
    }
}
*/

@Composable
fun FeatureHistoryView(viewModel: FeatureHistoryViewModel) {
    val dataPoints by viewModel.dataPoints.observeAsState(emptyList())
    val weekdayNames = getWeekDayNames(LocalContext.current)
    val isDuration by viewModel.isDuration.observeAsState(false)
    val isTracker by viewModel.isTracker.observeAsState(false)
    val featureInfo by viewModel.showFeatureInfo.observeAsState()
    val dataPointInfo by viewModel.showDataPointInfo.observeAsState()

    if (dataPoints.isEmpty()) {
        EmptyScreenText(textId = R.string.no_data_points_history_fragment_hint)
    } else {
        LazyColumn(modifier = Modifier.padding(dimensionResource(id = R.dimen.card_margin_small))) {
            items(dataPoints) {
                DataPoint(
                    dataPoint = it,
                    viewModel = viewModel,
                    weekdayNames = weekdayNames,
                    isDuration = isDuration,
                    isTracker = isTracker
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    featureInfo?.let {
        FeatureInfoDialog(
            feature = it,
            onDismissRequest = viewModel::onHideFeatureInfo
        )
    }

    dataPointInfo?.let {
        DataPointInfoDialog(
            dataPoint = it,
            weekdayNames = weekdayNames,
            isDuration = isDuration,
            onDismissRequest = viewModel::onDismissDataPoint
        )
    }

    if (viewModel.showDeleteConfirmDialog.observeAsState(false).value) {
        ConfirmCancelDialog(
            body = R.string.ru_sure_del_data_point,
            onDismissRequest = viewModel::onDeleteDismissed,
            onConfirm = viewModel::onDeleteConfirmed
        )
    }

    if (viewModel.showUpdateDialog.observeAsState(false).value) {
        UpdateDialog(viewModel = viewModel)
    }
}

@Composable
private fun UpdateDialog(
    viewModel: UpdateDialogViewModel
) = SlimConfirmCancelDialog(
    onDismissRequest = viewModel::onCancelUpdate,
    onConfirm = viewModel::onUpdateClicked,
    continueText = R.string.update
) {
    val isDuration by viewModel.isDuration.observeAsState(false)

    Text(
        "Update all data points: ",
        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
        fontWeight = MaterialTheme.typography.headlineSmall.fontWeight,
    )
    SpacingLarge()

    Text(
        "Where: ",
        fontSize = MaterialTheme.typography.labelLarge.fontSize,
        fontWeight = MaterialTheme.typography.labelLarge.fontWeight,
    )
    SpacingSmall()
    CheckboxLabelRow(
        checked = viewModel.whereValueEnabled.observeAsState(false).value,
        onCheckedChanged = viewModel::setWhereValueEnabled,
        label = "Value"
    ) {
        if (isDuration) {
            DurationInput(durationInputViewModel = viewModel.whereDurationViewModel)
        } else {
            ValueInputTextField(
                value = viewModel.whereValue.observeAsState("").value,
                onDefaultValueChanged = viewModel::setWhereValue
            )
        }
    }
    SpacingSmall()
    CheckboxLabelRow(
        checked = viewModel.whereLabelEnabled.observeAsState(false).value,
        onCheckedChanged = viewModel::setWhereLabelEnabled,
        label = "Label"
    ) {
        OutlinedTextField(
            value = viewModel.whereLabel.observeAsState("").value,
            onValueChange = viewModel::setWhereLabel,
            singleLine = true
        )
    }

    SpacingLarge()

    Text(
        "To: ",
        fontSize = MaterialTheme.typography.labelLarge.fontSize,
        fontWeight = MaterialTheme.typography.labelLarge.fontWeight,
    )

    SpacingSmall()
    CheckboxLabelRow(
        checked = viewModel.toValueEnabled.observeAsState(false).value,
        onCheckedChanged = viewModel::setToValueEnabled,
        label = "Value"
    ) {
        if (isDuration) {
            DurationInput(durationInputViewModel = viewModel.toDurationViewModel)
        } else {
            ValueInputTextField(
                value = viewModel.toValue.observeAsState("").value,
                onDefaultValueChanged = viewModel::setToValue
            )
        }
    }
    SpacingSmall()
    CheckboxLabelRow(
        checked = viewModel.toLabelEnabled.observeAsState(false).value,
        onCheckedChanged = viewModel::setToLabelEnabled,
        label = "Label"
    ) {
        OutlinedTextField(
            value = viewModel.toLabel.observeAsState("").value,
            onValueChange = viewModel::setToLabel,
            singleLine = true
        )
    }
}

@Composable
fun CheckboxLabelRow(
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    label: String,
    input: @Composable (modifier: Modifier) -> Unit
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
        .alpha(if (checked) 1.0f else MaterialTheme.colorScheme.disabledAlpha())
        .fillMaxWidth()
        .border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
            shape = MaterialTheme.shapes.small
        )
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onCheckedChanged(!checked) }
            .fillMaxWidth()
    ) {
        Checkbox(checked, onCheckedChanged)
        SpacingLarge()
        Text(text = label)
    }
    if (checked) input(Modifier.fillMaxWidth())
}

@Composable
private fun DataPoint(
    dataPoint: DataPoint,
    viewModel: FeatureHistoryViewModel,
    weekdayNames: List<String>,
    isDuration: Boolean,
    isTracker: Boolean
) = ElevatedCard(
    modifier = Modifier.clickable { viewModel.onDataPointClicked(dataPoint) }
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(dimensionResource(id = R.dimen.card_margin_small))
    ) {
        Text(
            text = formatDayMonthYearHourMinuteWeekDayTwoLines(
                LocalContext.current,
                weekdayNames,
                dataPoint.timestamp
            ),
            textAlign = TextAlign.Center
        )
        SpacingSmall()
        DataPointValueAndDescription(
            modifier = Modifier.weight(1f),
            dataPoint = dataPoint,
            isDuration = isDuration
        )
        if (isTracker) {
            IconButton(onClick = { viewModel.onEditClicked(dataPoint) }) {
                Icon(
                    painter = painterResource(id = R.drawable.edit_icon),
                    contentDescription = stringResource(id = R.string.edit_data_point_button_content_description),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = { viewModel.onDeleteClicked(dataPoint) }) {
                Icon(
                    painter = painterResource(id = R.drawable.delete_icon),
                    contentDescription = stringResource(id = R.string.delete_data_point_button_content_description),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}