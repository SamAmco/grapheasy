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
package com.samco.trackandgraph.graphstatinput.configviews.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.GraphEndDate
import com.samco.trackandgraph.base.database.dto.TimeHistogram
import com.samco.trackandgraph.base.database.dto.TimeHistogramWindow
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.DefaultDispatcher
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.model.di.MainDispatcher
import com.samco.trackandgraph.graphstatinput.GraphStatConfigEvent
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.SingleFeatureConfigBehaviour
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.SingleFeatureConfigBehaviourImpl
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.TimeRangeConfigBehaviour
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.TimeRangeConfigBehaviourImpl
import com.samco.trackandgraph.graphstatproviders.GraphStatInteractorProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@HiltViewModel
class TimeHistogramConfigViewModel @Inject constructor(
    @IODispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val default: CoroutineDispatcher,
    @MainDispatcher private val ui: CoroutineDispatcher,
    gsiProvider: GraphStatInteractorProvider,
    dataInteractor: DataInteractor,
    private val timeRangeConfigBehaviour: TimeRangeConfigBehaviourImpl = TimeRangeConfigBehaviourImpl(),
    private val singleFeatureConfigBehaviour: SingleFeatureConfigBehaviourImpl = SingleFeatureConfigBehaviourImpl(),
) : GraphStatConfigViewModelBase<GraphStatConfigEvent.ConfigData.TimeHistogramConfigData>(
    io,
    default,
    ui,
    gsiProvider,
    dataInteractor
), TimeRangeConfigBehaviour by timeRangeConfigBehaviour,
    SingleFeatureConfigBehaviour by singleFeatureConfigBehaviour {

    init {
        timeRangeConfigBehaviour.initTimeRangeConfigBehaviour { onUpdate() }
        singleFeatureConfigBehaviour.initSingleFeatureConfigBehaviour(onUpdate = { onUpdate() })
    }

    var selectedWindow: TimeHistogramWindow by mutableStateOf(TimeHistogramWindow.DAY)
        private set

    var sumByCount: Boolean by mutableStateOf(false)
        private set

    private var timeHistogram = TimeHistogram(
        id = 0L,
        graphStatId = 0L,
        featureId = -1L,
        sampleSize = null,
        window = TimeHistogramWindow.DAY,
        sumByCount = false,
        endDate = GraphEndDate.Latest
    )

    override fun updateConfig() {
        timeHistogram = timeHistogram.copy(
            featureId = singleFeatureConfigBehaviour.featureId ?: -1L,
            sampleSize = timeRangeConfigBehaviour.selectedDuration.temporalAmount,
            window = selectedWindow,
            sumByCount = sumByCount,
            endDate = timeRangeConfigBehaviour.sampleEndingAt.asGraphEndDate()
        )
    }

    override fun getConfig(): GraphStatConfigEvent.ConfigData.TimeHistogramConfigData {
        return GraphStatConfigEvent.ConfigData.TimeHistogramConfigData(timeHistogram)
    }

    override suspend fun validate(): GraphStatConfigEvent.ValidationException? {
        return if (singleFeatureConfigBehaviour.featureId == -1L) {
            GraphStatConfigEvent.ValidationException(R.string.graph_stat_validation_no_line_graph_features)
        } else null
    }

    override fun onDataLoaded(config: Any?) {

        val timeHist = config as? TimeHistogram

        val featureMap = featurePathProvider.sortedFeatureMap()

        singleFeatureConfigBehaviour.onConfigLoaded(
            map = featureMap,
            featureId = timeHist?.featureId ?: featureMap.keys.first()
        )

        timeRangeConfigBehaviour.onConfigLoaded(
            sampleSize = timeHist?.sampleSize,
            endingAt = timeHist?.endDate
        )

        timeHist?.let {
            this.timeHistogram = config
            selectedWindow = config.window
            sumByCount = config.sumByCount
        }
    }

    fun updateWindow(window: TimeHistogramWindow) {
        this.selectedWindow = window
        onUpdate()
    }

    fun updateSumByCount(sumByCount: Boolean) {
        this.sumByCount = sumByCount
        onUpdate()
    }
}
