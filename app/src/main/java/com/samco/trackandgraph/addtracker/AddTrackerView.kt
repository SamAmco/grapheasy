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
package com.samco.trackandgraph.addtracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.TrackerSuggestionOrder
import com.samco.trackandgraph.base.database.dto.TrackerSuggestionType
import com.samco.trackandgraph.base.model.TrackerHelper
import com.samco.trackandgraph.ui.compose.ui.*

@Composable
fun AddTrackerView(viewModel: AddTrackerViewModel) {
    val focusRequester = FocusRequester()
    val isUpdateMode by viewModel.isUpdateMode.observeAsState(false)
    val errors by viewModel.errors.observeAsState(listOf())
    val openDialog by viewModel.showUpdateWarningAlertDialog.observeAsState(false)

    Surface(color = MaterialTheme.colors.background) {
        Column(Modifier.fillMaxSize()) {

            AddTrackerInputForm(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                focusRequester = focusRequester,
                isInUpdateMode = isUpdateMode,
                errors = errors
            )

            if (openDialog) UpdateWarningDialog(
                onDismissRequest = viewModel::onDismissUpdateWarningCancel,
                onConfirm = viewModel::onConfirmUpdate
            )

            LaunchedEffect(true) { focusRequester.requestFocus() }
        }
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AddTrackerInputForm(
    modifier: Modifier,
    viewModel: AddTrackerViewModel,
    focusRequester: FocusRequester,
    errors: List<AddTrackerViewModel.AddTrackerError>,
    isInUpdateMode: Boolean
) = FormSurface {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        val isDuration = viewModel.isDuration.observeAsState(false)

        FormLabel(text = stringResource(id = R.string.tracker_name))
        NameInput(
            viewModel,
            focusManager,
            focusRequester,
            keyboardController
        )
        FormError(stringResource(id = R.string.tracker_name_cannot_be_null), errors.contains(AddTrackerViewModel.AddTrackerError.NoName))
        FormError(stringResource(id = R.string.tracker_with_that_name_exists), errors.contains(AddTrackerViewModel.AddTrackerError.NameAlreadyExists))

        FormFieldSeparator()

        FormLabel(
            text = stringResource(id = R.string.description),
            isOptional = true
        )
        DescriptionInput(viewModel)

        FormFieldSeparator()

        DurationCheckbox(isDuration.value, viewModel)

        val shouldShowConversionSpinner =
            viewModel.shouldShowDurationConversionModeSpinner.observeAsState(false)
        val durationConversionMode = viewModel.durationNumericConversionMode.observeAsState()

        if (shouldShowConversionSpinner.value) {
            FormFieldSeparator()

            DurationConversionModeInput(
                isDuration.value,
                durationConversionMode.value,
                viewModel
            )
        }

        FormFieldSeparator()

        AdvancedOptions(viewModel)

        val isInErrorState = remember(errors) { errors.isNotEmpty() }

        FormFieldSeparator()

        FormSaveButton(
            isInErrorState = isInErrorState,
            isInUpdateMode = isInUpdateMode,
            onCreateUpdateClicked = viewModel::onCreateUpdateClicked,
        )
    }
}

@Composable
private fun AdvancedOptions(viewModel: AddTrackerViewModel) = Column {

    var advancedOptionsExpanded by rememberSaveable { mutableStateOf(false) }

    AdvancedSectionHeader(advancedOptionsExpanded) {
        advancedOptionsExpanded = !advancedOptionsExpanded
    }

    AnimatedVisibility(visible = advancedOptionsExpanded) {
        Column {
            FormFieldSeparator()

            DefaultValueOptions(viewModel)

            FormFieldSeparator()

            FormSection {
                Column {
                    Text(
                        text = stringResource(id = R.string.suggestions),
                        style = MaterialTheme.typography.subtitle2
                    )

                    FormFieldSeparator()

                    FormLabel(text = stringResource(id = R.string.suggestions_type))
                    SuggestionType(viewModel)

                    FormFieldSeparator()

                    FormLabel(text = stringResource(id = R.string.suggestions_order))
                    SuggestionOrder(viewModel)
                }
            }
        }
    }
}

@Composable
fun SuggestionType(viewModel: AddTrackerViewModel) {
    val selectedSuggestionType by viewModel.suggestionType.observeAsState(
        TrackerSuggestionType.LABEL_ONLY
    )

    val suggestionTypeMap = mapOf(
        TrackerSuggestionType.VALUE_AND_LABEL to stringResource(R.string.value_and_label),
        TrackerSuggestionType.VALUE_ONLY to stringResource(R.string.value_only),
        TrackerSuggestionType.LABEL_ONLY to stringResource(R.string.label_only),
        TrackerSuggestionType.NONE to stringResource(R.string.none)
    )

    FormSpinner(
        strings = suggestionTypeMap,
        selectedItem = selectedSuggestionType,
        onItemSelected = { viewModel.onSuggestionTypeChanged(it) }
    )
}

@Composable
fun SuggestionOrder(viewModel: AddTrackerViewModel) {

    val selectedSuggestionType by viewModel.suggestionType
        .observeAsState(TrackerSuggestionType.LABEL_ONLY)

    if (selectedSuggestionType != TrackerSuggestionType.NONE) {
        val selectedSuggestionOrder by viewModel.suggestionOrder.observeAsState(
            TrackerSuggestionOrder.LABEL_ASCENDING
        )

        val suggestionOrderMap = mapOf(
            TrackerSuggestionOrder.VALUE_ASCENDING to stringResource(R.string.value_ascending),
            TrackerSuggestionOrder.VALUE_DESCENDING to stringResource(R.string.value_descending),
            TrackerSuggestionOrder.LABEL_ASCENDING to stringResource(R.string.label_ascending),
            TrackerSuggestionOrder.LABEL_DESCENDING to stringResource(R.string.label_descending),
            TrackerSuggestionOrder.LATEST to stringResource(R.string.latest),
            TrackerSuggestionOrder.OLDEST to stringResource(R.string.oldest)
        )

        FormSpinner(
            strings = suggestionOrderMap,
            selectedItem = selectedSuggestionOrder,
            onItemSelected = { viewModel.onSuggestionOrderChanged(it) }
        )
    }
}

@Composable
private fun AdvancedSectionHeader(
    expanded: Boolean,
    onClick: () -> Unit = {}
) = Row(
    modifier = Modifier.clickable(
        onClick = onClick,
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ),
    verticalAlignment = Alignment.CenterVertically
) {
    Divider()
    SpacingSmall()
    Text(
        text = stringResource(id = R.string.advanced_options),
        style = MaterialTheme.typography.subtitle1
    )
    Icon(
        modifier = Modifier.rotate(if (expanded) 180f else 0f),
        painter = painterResource(id = R.drawable.down_arrow),
        contentDescription = null
    )
    SpacingSmall()
    Divider()
}

@Composable
private fun RowScope.Divider() {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(1.dp)
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.2f))
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DefaultValueOptions(viewModel: AddTrackerViewModel) {
    val hasDefaultValue = viewModel.hasDefaultValue.observeAsState(false)
    val isDuration = viewModel.isDuration.observeAsState(false)
    val focusManager = LocalFocusManager.current

    FormSection {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            DefaultValueCheckbox(hasDefaultValue.value, viewModel)

            if (hasDefaultValue.value) {
                Divider()

                FormLabel(text = stringResource(id = R.string.value))

                if (isDuration.value) {
                    FormDurationInput(
                        hoursFieldValue = viewModel.hours,
                        minutesFieldValue = viewModel.minutes,
                        secondsFieldValue = viewModel.seconds,
                        onHoursValueChange = viewModel::setHoursText,
                        onMinutesValueChange = viewModel::setMinutesText,
                        onSecondsValueChange = viewModel::setSecondsText
                    )
                }
                else {
                    FormTextInput(
                        textFieldValue = viewModel.defaultValue,
                        onValueChange = viewModel::onDefaultValueChanged,
                        focusManager = focusManager,
                        isNumeric = true)
                }

                FormFieldSeparator()

                FormLabel(text = stringResource(id = R.string.label))
                FormTextInput(
                    textFieldValue = viewModel.defaultLabel,
                    onValueChange = viewModel::onDefaultLabelChanged
                )
            }
        }
    }
}

@Composable
private fun DurationConversionModeInput(
    isDuration: Boolean,
    durationConversionMode: TrackerHelper.DurationNumericConversionMode?,
    viewModel: AddTrackerViewModel
) {
    val strings = mapOf(
        TrackerHelper.DurationNumericConversionMode.HOURS to stringResource(id = R.string.hours),
        TrackerHelper.DurationNumericConversionMode.MINUTES to stringResource(id = R.string.minutes),
        TrackerHelper.DurationNumericConversionMode.SECONDS to stringResource(id = R.string.seconds)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        val name =
            if (isDuration) stringResource(id = R.string.numeric_to_duration_mode_header)
            else stringResource(id = R.string.duration_to_numeric_mode_header)

        FormLabel(text = name)
        FormSpinner(
            strings = strings,
            selectedItem = durationConversionMode
                ?: TrackerHelper.DurationNumericConversionMode.HOURS,
            onItemSelected = viewModel::onDurationNumericConversionModeChanged
        )
    }
}

@Composable
private fun DefaultValueCheckbox(
    hasDefaultValue: Boolean,
    viewModel: AddTrackerViewModel
) {
    FormSwitchInput(
        checked = hasDefaultValue,
        onCheckedChange = { viewModel.onHasDefaultValueChanged(it) },
        text = stringResource(id = R.string.use_default_value)
    )
}

@Composable
private fun DurationCheckbox(
    isDuration: Boolean,
    viewModel: AddTrackerViewModel
) {
    FormSwitchInput(
        checked = isDuration,
        onCheckedChange = { viewModel.onIsDurationCheckChanged(it) },
        text = stringResource(id = R.string.tracker_type)
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DescriptionInput(
    viewModel: AddTrackerViewModel
) = FormTextInput(
    textFieldValue = viewModel.trackerDescription,
    onValueChange = viewModel::onTrackerDescriptionChanged,
    singleLine = false
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NameInput(
    viewModel: AddTrackerViewModel,
    focusManager: FocusManager,
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?
) = FormTextInput(
    textFieldValue = viewModel.trackerName,
    onValueChange = viewModel::onTrackerNameChanged,
    focusManager = focusManager,
    focusRequester = focusRequester,
    keyboardController = keyboardController
)

