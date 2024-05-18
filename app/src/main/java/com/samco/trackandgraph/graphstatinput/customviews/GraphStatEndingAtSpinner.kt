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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.GraphEndDate
import com.samco.trackandgraph.base.helpers.formatDayMonthYear
import com.samco.trackandgraph.ui.compose.compositionlocals.LocalSettings
import com.samco.trackandgraph.ui.compose.ui.FormLabel
import com.samco.trackandgraph.ui.compose.ui.FormSpinner
import com.samco.trackandgraph.ui.compose.ui.showDateDialog
import org.threeten.bp.OffsetDateTime

enum class SampleEndingAtOption {
    NOW, LATEST, CUSTOM
}

sealed interface SampleEndingAt {
    val option: SampleEndingAtOption

    fun asGraphEndDate(): GraphEndDate

    companion object {
        fun fromGraphEndDate(endDate: GraphEndDate): SampleEndingAt {
            return when (endDate) {
                is GraphEndDate.Latest -> Latest
                is GraphEndDate.Now -> Now
                is GraphEndDate.Date -> Custom(endDate.date)
            }
        }
    }

    object Latest : SampleEndingAt {
        override val option = SampleEndingAtOption.LATEST
        override fun asGraphEndDate() = GraphEndDate.Latest
    }

    object Now : SampleEndingAt {
        override val option = SampleEndingAtOption.NOW
        override fun asGraphEndDate() = GraphEndDate.Now
    }

    data class Custom(val dateTime: OffsetDateTime) : SampleEndingAt {
        override val option = SampleEndingAtOption.CUSTOM
        override fun asGraphEndDate() = GraphEndDate.Date(dateTime)
    }
}

@Composable
fun GraphStatEndingAtSpinner(
    modifier: Modifier,
    sampleEndingAt: SampleEndingAt,
    onSampleEndingAtChanged: (SampleEndingAt) -> Unit
) {
    FormLabel(text = stringResource(id = R.string.ending_at_colon))

    val strLatest = stringResource(id = R.string.ending_at_latest)
    val strCustom = stringResource(id = R.string.ending_at_custom_date)
    val strNow = stringResource(id = R.string.ending_at_now)

    val spinnerItems = remember {
        mapOf(
            SampleEndingAtOption.LATEST to strLatest,
            SampleEndingAtOption.NOW to strNow,
            SampleEndingAtOption.CUSTOM to strCustom
        )
    }

    val context = LocalContext.current
    val firstDayOfWeek = LocalSettings.current.firstDayOfWeek

    FormSpinner(
        modifier = modifier,
        strings = spinnerItems,
        selectedItem = sampleEndingAt.option,
        onItemSelected = { option ->
            when (option) {
                SampleEndingAtOption.LATEST -> onSampleEndingAtChanged(SampleEndingAt.Latest)
                SampleEndingAtOption.NOW -> onSampleEndingAtChanged(SampleEndingAt.Now)
                SampleEndingAtOption.CUSTOM -> showDateDialog(
                    context = context,
                    firstDayOfWeek = firstDayOfWeek,
                    onDateSelected = {
                        onSampleEndingAtChanged(SampleEndingAt.Custom(it))
                    }
                )
            }
        },
        selectedItemTransform = { item ->
            when (item) {
                SampleEndingAtOption.LATEST -> strLatest
                SampleEndingAtOption.NOW -> strNow
                SampleEndingAtOption.CUSTOM -> {
                    if (sampleEndingAt is SampleEndingAt.Custom)
                        formatDayMonthYear(context, sampleEndingAt.dateTime)
                    else strCustom
                }
            }
        }
    )
}