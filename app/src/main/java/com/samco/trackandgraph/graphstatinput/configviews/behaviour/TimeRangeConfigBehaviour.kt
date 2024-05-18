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
package com.samco.trackandgraph.graphstatinput.configviews.behaviour

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.samco.trackandgraph.base.database.dto.GraphEndDate
import com.samco.trackandgraph.graphstatinput.customviews.GraphStatSampleSize
import com.samco.trackandgraph.graphstatinput.customviews.SampleEndingAt
import org.threeten.bp.temporal.TemporalAmount
import javax.inject.Inject

interface EndingAtConfigBehaviour {
    val sampleEndingAt: SampleEndingAt
    fun updateSampleEndingAt(endingAt: SampleEndingAt)
}

interface TimeRangeConfigBehaviour : EndingAtConfigBehaviour {
    val selectedDuration: GraphStatSampleSize
    fun updateDuration(duration: GraphStatSampleSize)
}

class TimeRangeConfigBehaviourImpl @Inject constructor() : TimeRangeConfigBehaviour {
    private lateinit var onUpdate: () -> Unit

    override var selectedDuration by mutableStateOf<GraphStatSampleSize>(GraphStatSampleSize.AllData)

    override var sampleEndingAt by mutableStateOf<SampleEndingAt>(SampleEndingAt.Latest)

    fun initTimeRangeConfigBehaviour(onUpdate: () -> Unit) {
        this.onUpdate = onUpdate
    }

    fun onConfigLoaded(
        sampleSize: TemporalAmount?,
        endingAt: GraphEndDate?
    ) {
        sampleSize?.let { selectedDuration = GraphStatSampleSize.fromTemporalAmount(it) }
        endingAt?.let { sampleEndingAt = SampleEndingAt.fromGraphEndDate(it) }
    }

    override fun updateDuration(duration: GraphStatSampleSize) {
        selectedDuration = duration
        onUpdate()
    }

    override fun updateSampleEndingAt(endingAt: SampleEndingAt) {
        sampleEndingAt = endingAt
        onUpdate()
    }
}

