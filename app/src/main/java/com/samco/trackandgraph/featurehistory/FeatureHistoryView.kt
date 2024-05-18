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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.samco.trackandgraph.R
import com.samco.trackandgraph.adddatapoint.AddDataPointsDialog
import com.samco.trackandgraph.adddatapoint.AddDataPointsNavigationViewModel
import com.samco.trackandgraph.adddatapoint.AddDataPointsViewModelImpl
import com.samco.trackandgraph.base.database.dto.Tracker
import com.samco.trackandgraph.base.helpers.formatDayMonthYearHourMinuteWeekDayTwoLines
import com.samco.trackandgraph.base.helpers.getWeekDayNames
import com.samco.trackandgraph.ui.compose.theming.tngColors
import com.samco.trackandgraph.ui.compose.ui.*

@Composable
fun FeatureHistoryView(viewModel: FeatureHistoryViewModel) {
    val dateScrollData = viewModel.dateScrollData.observeAsState().value
    val weekdayNames = getWeekDayNames(LocalContext.current)
    val isDuration by viewModel.isDuration.observeAsState(false)
    val tracker by viewModel.tracker.observeAsState(null)
    val featureInfo by viewModel.showFeatureInfo.observeAsState()
    val dataPointInfo by viewModel.showDataPointInfo.observeAsState()
    val dataPointDialogViewModel = hiltViewModel<AddDataPointsViewModelImpl>()

    if (dateScrollData == null || dateScrollData.items.isEmpty()) {
        EmptyScreenText(textId = R.string.no_data_points_history_fragment_hint)
    } else {
        DateScrollLazyColumn(
            modifier = Modifier.padding(dimensionResource(id = R.dimen.card_margin_small)),
            data = dateScrollData
        ) {
            DataPoint(
                dataPoint = it,
                addDataPointsViewModel = dataPointDialogViewModel,
                viewModel = viewModel,
                weekdayNames = weekdayNames,
                isDuration = isDuration,
                tracker = tracker
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.card_margin_small)))
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
            dataPoint = it.toDataPoint(),
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

    if (viewModel.showUpdateWarning.observeAsState(false).value) {
        UpdateWarningDialog(
            viewModel::onCancelUpdateWarning,
            viewModel::onConfirmUpdateWarning
        )
    }

    if (viewModel.isUpdating.observeAsState(false).value) {
        LoadingOverlay()
    }

    if (!dataPointDialogViewModel.hidden.observeAsState(true).value) {
        AddDataPointsDialog(
            viewModel = dataPointDialogViewModel,
            onDismissRequest = { dataPointDialogViewModel.reset() }
        )
    }
}

@Composable
private fun UpdateWarningDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) = ConfirmCancelDialog(
    body = R.string.ru_sure_update_data,
    onDismissRequest = onDismissRequest,
    onConfirm = onConfirm
)


@Composable
private fun UpdateDialog(
    viewModel: UpdateDialogViewModel
) = CustomConfirmCancelDialog(
    onDismissRequest = viewModel::onCancelUpdate,
    customWidthPercentage = 0.9f,
    onConfirm = viewModel::onUpdateClicked,
    continueText = R.string.update,
    continueEnabled = viewModel.updateButtonEnabled.observeAsState(false).value
) {

    Text(
        stringResource(R.string.update_all_data_points),
        fontSize = MaterialTheme.typography.h4.fontSize,
        fontWeight = MaterialTheme.typography.h4.fontWeight,
    )
    SpacingLarge()

    Text(
        stringResource(R.string.where_colon),
        fontSize = MaterialTheme.typography.subtitle1.fontSize,
        fontWeight = MaterialTheme.typography.subtitle1.fontWeight,
    )
    SpacingSmall()
    WhereValueInput(viewModel)
    SpacingSmall()
    WhereLabelInput(viewModel)

    SpacingLarge()

    Text(
        stringResource(R.string.to_colon),
        fontSize = MaterialTheme.typography.subtitle1.fontSize,
        fontWeight = MaterialTheme.typography.subtitle1.fontWeight,
    )

    SpacingSmall()
    ToValueInput(viewModel)
    SpacingSmall()
    ToLabelInput(viewModel)
}

@Composable
private fun ToLabelInput(viewModel: UpdateDialogViewModel) {
    val focusRequester = remember { FocusRequester() }

    CheckboxLabeledExpandingSection(
        checked = viewModel.toLabelEnabled.observeAsState(false).value,
        onCheckedChanged = viewModel::setToLabelEnabled,
        label = stringResource(R.string.label_equals),
        focusRequester = focusRequester
    ) {
        LabelInputTextField(
            modifier = it,
            textFieldValue = viewModel.toLabel,
            onValueChange = viewModel::setToTextLabel
        )
    }
}

@Composable
private fun ToValueInput(viewModel: UpdateDialogViewModel) {
    val focusRequester = remember { FocusRequester() }
    val isDuration by viewModel.isDuration.observeAsState(false)

    CheckboxLabeledExpandingSection(
        checked = viewModel.toValueEnabled.observeAsState(false).value,
        onCheckedChanged = viewModel::setToValueEnabled,
        label = stringResource(R.string.value_equals),
        focusRequester = focusRequester
    ) {
        if (isDuration) {
            DurationInput(
                modifier = it,
                viewModel = viewModel.toDurationViewModel
            )
        } else {
            ValueInputTextField(
                modifier = it,
                textFieldValue = viewModel.toValue,
                onValueChange = viewModel::setToTextValue
            )
        }
    }
}

@Composable
private fun WhereLabelInput(viewModel: UpdateDialogViewModel) {
    val focusRequester = remember { FocusRequester() }

    CheckboxLabeledExpandingSection(
        checked = viewModel.whereLabelEnabled.observeAsState(false).value,
        onCheckedChanged = viewModel::setWhereLabelEnabled,
        label = stringResource(R.string.label_equals),
        focusRequester = focusRequester
    ) {
        LabelInputTextField(
            modifier = it,
            textFieldValue = viewModel.whereLabel,
            onValueChange = viewModel::setWhereTextLabel,
        )
    }
}

@Composable
private fun WhereValueInput(
    viewModel: UpdateDialogViewModel
) {
    val focusRequester = remember { FocusRequester() }
    val isDuration by viewModel.isDuration.observeAsState(false)

    CheckboxLabeledExpandingSection(
        checked = viewModel.whereValueEnabled.observeAsState(false).value,
        onCheckedChanged = viewModel::setWhereValueEnabled,
        label = stringResource(R.string.value_equals),
        focusRequester = focusRequester
    ) {
        if (isDuration) {
            DurationInput(
                modifier = it,
                viewModel = viewModel.whereDurationViewModel
            )
        } else {
            ValueInputTextField(
                modifier = it,
                textFieldValue = viewModel.whereValue,
                onValueChange = viewModel::setWhereTextValue,
            )
        }
    }
}


@Composable
private fun DataPoint(
    dataPoint: DataPointInfo,
    addDataPointsViewModel: AddDataPointsNavigationViewModel,
    viewModel: FeatureHistoryViewModel,
    weekdayNames: List<String>,
    isDuration: Boolean,
    tracker: Tracker?
) = Card(
    modifier = Modifier
        .clickable { viewModel.onDataPointClicked(dataPoint) },
    elevation = dimensionResource(id = R.dimen.card_elevation)
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
                dataPoint.date
            ),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.body2,
        )
        SpacingSmall()
        DataPointValueAndDescription(
            modifier = Modifier.weight(1f),
            dataPoint = dataPoint.toDataPoint(),
            isDuration = isDuration
        )
        if (tracker != null) {
            IconButton(onClick = {
                addDataPointsViewModel.showAddDataPointDialog(
                    trackerId = tracker.id,
                    dataPointTimestamp = dataPoint.date
                )
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.edit_icon),
                    contentDescription = stringResource(id = R.string.edit_data_point_button_content_description),
                    tint = MaterialTheme.tngColors.secondary
                )
            }
            IconButton(onClick = { viewModel.onDeleteClicked(dataPoint) }) {
                Icon(
                    painter = painterResource(id = R.drawable.delete_icon),
                    contentDescription = stringResource(id = R.string.delete_data_point_button_content_description),
                    tint = MaterialTheme.tngColors.primary
                )
            }
        }
    }
}
