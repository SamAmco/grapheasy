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

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.ui.CustomConfirmCancelDialog
import com.samco.trackandgraph.ui.compose.ui.FormLabel
import com.samco.trackandgraph.ui.compose.ui.FormSpinner
import com.samco.trackandgraph.ui.compose.ui.SpacingSmall
import com.samco.trackandgraph.ui.compose.ui.TextMapSpinner
import org.threeten.bp.Duration
import org.threeten.bp.Period
import org.threeten.bp.temporal.ChronoUnit
import org.threeten.bp.temporal.TemporalAmount

enum class GraphStatSampleSizeOption {
    ALL_DATA,
    A_DAY,
    A_WEEK,
    A_MONTH,
    THREE_MONTHS,
    SIX_MONTHS,
    A_YEAR,
    CUSTOM
}

sealed class GraphStatSampleSize private constructor(
    val temporalAmount: TemporalAmount?,
    val option: GraphStatSampleSizeOption
) {

    object AllData : GraphStatSampleSize(
        null,
        GraphStatSampleSizeOption.ALL_DATA
    )

    object ADay : GraphStatSampleSize(
        Period.ofDays(1),
        GraphStatSampleSizeOption.A_DAY
    )

    object AWeek : GraphStatSampleSize(
        Period.ofWeeks(1),
        GraphStatSampleSizeOption.A_WEEK
    )

    object AMonth : GraphStatSampleSize(
        Period.ofMonths(1),
        GraphStatSampleSizeOption.A_MONTH
    )

    object ThreeMonths : GraphStatSampleSize(
        Period.ofMonths(3),
        GraphStatSampleSizeOption.THREE_MONTHS
    )

    object SixMonths : GraphStatSampleSize(
        Period.ofMonths(6),
        GraphStatSampleSizeOption.SIX_MONTHS
    )

    object AYear : GraphStatSampleSize(
        Period.ofYears(1),
        GraphStatSampleSizeOption.A_YEAR
    )

    data class Custom(val customTemporalAmount: TemporalAmount) : GraphStatSampleSize(
        customTemporalAmount,
        GraphStatSampleSizeOption.CUSTOM
    ) {
        constructor(amount: Long, unit: ChronoUnit) : this(
            getTemporalAmount(QuantityAndUnit(amount, unit))
        )
    }

    companion object {

        private val valueMap = mapOf(
            null to AllData,

            //These are here for legacy reasons. Some people might have graphs from the old
            // version which did not use periods and only used durations. As such their selected
            // duration will not be recognised unless we map the old versions too.
            Duration.ofDays(1) to ADay,
            Duration.ofDays(7) to AWeek,
            Duration.ofDays(31) to AMonth,
            Duration.ofDays(93) to ThreeMonths,
            Duration.ofDays(183) to SixMonths,
            Duration.ofDays(365) to AYear,

            Period.ofDays(1) to ADay,
            Period.ofWeeks(1) to AWeek,
            Period.ofMonths(1) to AMonth,
            Period.ofMonths(3) to ThreeMonths,
            Period.ofMonths(6) to SixMonths,
            Period.ofYears(1) to AYear
        )

        fun fromTemporalAmount(temporalAmount: TemporalAmount?): GraphStatSampleSize {
            return temporalAmount?.let {
                valueMap[temporalAmount] ?: Custom(temporalAmount)
            } ?: AllData
        }

        fun fromOption(option: GraphStatSampleSizeOption): GraphStatSampleSize {
            return mapOf(
                GraphStatSampleSizeOption.ALL_DATA to AllData,
                GraphStatSampleSizeOption.A_DAY to ADay,
                GraphStatSampleSizeOption.A_WEEK to AWeek,
                GraphStatSampleSizeOption.A_MONTH to AMonth,
                GraphStatSampleSizeOption.THREE_MONTHS to ThreeMonths,
                GraphStatSampleSizeOption.SIX_MONTHS to SixMonths,
                GraphStatSampleSizeOption.A_YEAR to AYear
            )[option] ?: throw IllegalArgumentException("Unknown option: $option")
        }
    }
}


@Composable
fun GraphStatDurationSpinner(
    modifier: Modifier = Modifier,
    label: String = stringResource(id = R.string.sample_size),
    selectedDuration: GraphStatSampleSize,
    onDurationSelected: (GraphStatSampleSize) -> Unit
) {

    val context = LocalContext.current

    var showCustomDialog by remember { mutableStateOf(false) }

    if (showCustomDialog) {
        CustomDurationDialog(
            selectedDuration,
            onDismissRequest = { showCustomDialog = false },
            onDurationSelected = {
                showCustomDialog = false
                onDurationSelected(it)
            }
        )
    }

    FormLabel(text = label)

    val strings = stringArrayResource(id = R.array.graph_time_durations)

    val spinnerItems = remember { mapOf(
        GraphStatSampleSizeOption.ALL_DATA to strings[0],
        GraphStatSampleSizeOption.A_DAY to strings[1],
        GraphStatSampleSizeOption.A_WEEK to strings[2],
        GraphStatSampleSizeOption.A_MONTH to strings[3],
        GraphStatSampleSizeOption.THREE_MONTHS to strings[4],
        GraphStatSampleSizeOption.SIX_MONTHS to strings[5],
        GraphStatSampleSizeOption.A_YEAR to strings[6],
        GraphStatSampleSizeOption.CUSTOM to strings[7]
    )}

    FormSpinner(
        strings = spinnerItems,
        selectedItem = selectedDuration.option,
        onItemSelected = {
            if (it == GraphStatSampleSizeOption.CUSTOM) {
                showCustomDialog = true
            } else {
                onDurationSelected(GraphStatSampleSize.fromOption(it))
            }
        },
        selectedItemTransform = { item ->
            when (item) {
                GraphStatSampleSizeOption.CUSTOM -> {
                    selectedDuration.temporalAmount?.let {
                        formatTemporalAmountAsSampleSize(context, it)
                    } ?: spinnerItems[item]
                }

                else -> spinnerItems[item]
            }
        }
    )
}

private data class QuantityAndUnit(
    val quantity: Long,
    val unit: ChronoUnit
)

private fun getQuantityAndUnit(temporalAmount: TemporalAmount): QuantityAndUnit {
    for (unit in temporalAmount.units) {
        val quantity = temporalAmount.get(unit)
        if (quantity > 0) {
            return when {
                unit == ChronoUnit.DAYS && quantity % 7 == 0L -> QuantityAndUnit(quantity / 7, ChronoUnit.WEEKS)
                unit == ChronoUnit.SECONDS && quantity % (60*60) == 0L -> QuantityAndUnit(quantity / (60*60), ChronoUnit.HOURS)
                unit == ChronoUnit.SECONDS && quantity % 60 == 0L -> QuantityAndUnit(quantity / 60, ChronoUnit.MINUTES)
                unit == ChronoUnit.NANOS -> QuantityAndUnit(quantity / 1_000_000_000L, ChronoUnit.SECONDS)
                else -> QuantityAndUnit(quantity, unit as ChronoUnit)
            }
        }
    }
    return QuantityAndUnit(0, ChronoUnit.DAYS)
}

private fun getTemporalAmount(quantityAndUnit: QuantityAndUnit): TemporalAmount {
    val quantity = quantityAndUnit.quantity
    return when (quantityAndUnit.unit) {
        ChronoUnit.YEARS -> Period.ofYears(quantity.toInt())
        ChronoUnit.MONTHS -> Period.ofMonths(quantity.toInt())
        ChronoUnit.WEEKS -> Period.ofWeeks(quantity.toInt())
        ChronoUnit.DAYS -> Period.ofDays(quantity.toInt())
        ChronoUnit.HOURS -> Duration.ofHours(quantity)
        ChronoUnit.MINUTES -> Duration.ofMinutes(quantity)
        ChronoUnit.SECONDS -> Duration.ofSeconds(quantity)
        else -> throw IllegalArgumentException("Unsupported unit: ${quantityAndUnit.unit}")
    }
}

private fun formatTemporalAmountAsSampleSize(
    context: Context,
    temporalAmount: TemporalAmount
): String {
    val quantityAndUnit = getQuantityAndUnit(temporalAmount)
    val quantity = quantityAndUnit.quantity.toInt()
    val res = context.resources

    return when (quantityAndUnit.unit) {
        ChronoUnit.YEARS -> res.getQuantityString(R.plurals.years, quantity, quantity)
        ChronoUnit.MONTHS -> res.getQuantityString(R.plurals.months, quantity, quantity)
        ChronoUnit.WEEKS -> res.getQuantityString(R.plurals.weeks, quantity, quantity)
        ChronoUnit.DAYS -> res.getQuantityString(R.plurals.days, quantity, quantity)
        ChronoUnit.HOURS -> res.getQuantityString(R.plurals.hours, quantity, quantity)
        ChronoUnit.MINUTES -> res.getQuantityString(R.plurals.minutes, quantity, quantity)
        ChronoUnit.SECONDS -> res.getQuantityString(R.plurals.seconds, quantity, quantity)
        else -> temporalAmount.toString()
    }
}

@Composable
private fun CustomDurationDialog(
    selectedDuration: GraphStatSampleSize,
    onDismissRequest: () -> Unit,
    onDurationSelected: (GraphStatSampleSize) -> Unit
) {
    val quantityAndUnit = remember(selectedDuration) {
        selectedDuration.temporalAmount?.let {
            getQuantityAndUnit(it)
        } ?: QuantityAndUnit(1, ChronoUnit.WEEKS)
    }

    var selectedNumber by remember(quantityAndUnit) {
        mutableStateOf(
            TextFieldValue(
                quantityAndUnit.quantity.toString(),
                selection = TextRange(quantityAndUnit.quantity.toString().length)
            )
        )
    }

    var selectedUnit by remember(quantityAndUnit) {
        mutableStateOf(quantityAndUnit.unit)
    }

    val strings = mapOf(
        ChronoUnit.SECONDS to stringResource(id = R.string.seconds_generic),
        ChronoUnit.MINUTES to stringResource(id = R.string.minutes_generic),
        ChronoUnit.HOURS to stringResource(id = R.string.hours_generic),
        ChronoUnit.DAYS to stringResource(id = R.string.days_generic),
        ChronoUnit.WEEKS to stringResource(id = R.string.weeks_generic),
        ChronoUnit.MONTHS to stringResource(id = R.string.months_generic),
        ChronoUnit.YEARS to stringResource(id = R.string.years_generic),
    )

    CustomConfirmCancelDialog(
        onDismissRequest = onDismissRequest,
        continueText = R.string.ok,
        onConfirm = {
            onDurationSelected(
                GraphStatSampleSize.Custom(
                    selectedNumber.text.toLongOrNull() ?: 0,
                    selectedUnit
                )
            )
        }
    ) {
        val focusRequester = remember { FocusRequester() }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .weight(0.6f),
                value = selectedNumber,
                textStyle = MaterialTheme.typography.h5.copy(
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { tfv ->
                    val filtered = tfv.copy(text = tfv.text.filter { it.isDigit() })
                    if (filtered != selectedNumber) selectedNumber = filtered
                }
            )

            SpacingSmall()

            TextMapSpinner(
                modifier = Modifier.weight(1f),
                strings = strings,
                selectedItem = selectedUnit,
                onItemSelected = {
                    if (it != selectedUnit) selectedUnit = it
                }
            )
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}