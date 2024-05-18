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
package com.samco.trackandgraph.graphstatinput.configviews.viewmodel

import androidx.lifecycle.viewModelScope
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.AverageTimeBetweenStat
import com.samco.trackandgraph.base.database.dto.GraphEndDate
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.DefaultDispatcher
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.model.di.MainDispatcher
import com.samco.trackandgraph.graphstatinput.GraphStatConfigEvent
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.*
import com.samco.trackandgraph.graphstatproviders.GraphStatInteractorProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

@HiltViewModel
class AverageTimeBetweenConfigViewModel @Inject constructor(
    @IODispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val default: CoroutineDispatcher,
    @MainDispatcher private val ui: CoroutineDispatcher,
    gsiProvider: GraphStatInteractorProvider,
    dataInteractor: DataInteractor,
    private val timeRangeConfigBehaviour: TimeRangeConfigBehaviourImpl = TimeRangeConfigBehaviourImpl(),
    private val filterableFeatureConfigBehaviour: FilterableFeatureConfigBehaviourImpl = FilterableFeatureConfigBehaviourImpl(),
    private val singleFeatureConfigBehaviour: SingleFeatureConfigBehaviourImpl = SingleFeatureConfigBehaviourImpl(),
) : GraphStatConfigViewModelBase<GraphStatConfigEvent.ConfigData.AverageTimeBetweenConfigData>(
    io,
    default,
    ui,
    gsiProvider,
    dataInteractor
), TimeRangeConfigBehaviour by timeRangeConfigBehaviour,
    FilterableFeatureConfigBehaviour by filterableFeatureConfigBehaviour,
    SingleFeatureConfigBehaviour by singleFeatureConfigBehaviour {

    init {
        timeRangeConfigBehaviour.initTimeRangeConfigBehaviour { onUpdate() }
        filterableFeatureConfigBehaviour.initFilterableFeatureConfigBehaviour(
            onUpdate = { onUpdate() },
            io = io,
            ui = ui,
            coroutineScope = viewModelScope,
            dataInteractor = dataInteractor
        )
        singleFeatureConfigBehaviour.initSingleFeatureConfigBehaviour(
            onUpdate = { onUpdate() },
            featureChangeCallback = { filterableFeatureConfigBehaviour.onFeatureIdUpdated(it) },
        )
    }

    private var averageTimeBetweenStat: AverageTimeBetweenStat = AverageTimeBetweenStat(
        id = 0,
        graphStatId = 0,
        featureId = -1L,
        fromValue = 0.0,
        toValue = 1.0,
        sampleSize = null,
        labels = listOf(),
        endDate = GraphEndDate.Latest,
        filterByRange = false,
        filterByLabels = false
    )

    override fun updateConfig() {
        averageTimeBetweenStat = averageTimeBetweenStat.copy(
            featureId = featureId ?: -1L,
            fromValue = fromValue.text.toDoubleOrNull() ?: 0.0,
            toValue = toValue.text.toDoubleOrNull() ?: 1.0,
            sampleSize = selectedDuration.temporalAmount,
            labels = selectedLabels,
            endDate = sampleEndingAt.asGraphEndDate(),
            filterByRange = filterByRange,
            filterByLabels = filterByLabel
        )
    }

    override fun getConfig(): GraphStatConfigEvent.ConfigData.AverageTimeBetweenConfigData {
        return GraphStatConfigEvent.ConfigData.AverageTimeBetweenConfigData(averageTimeBetweenStat)
    }

    override suspend fun validate(): GraphStatConfigEvent.ValidationException? {
        if (averageTimeBetweenStat.featureId == -1L)
            return GraphStatConfigEvent.ValidationException(R.string.graph_stat_validation_no_line_graph_features)
        if (averageTimeBetweenStat.filterByRange && (averageTimeBetweenStat.fromValue > averageTimeBetweenStat.toValue))
            return GraphStatConfigEvent.ValidationException(R.string.graph_stat_validation_invalid_value_stat_from_to)
        return null
    }

    override fun onDataLoaded(config: Any?) {

        val avStat = config as? AverageTimeBetweenStat

        val featureMap = featurePathProvider.sortedFeatureMap()
        val featureId = avStat?.featureId ?: featureMap.keys.first()

        singleFeatureConfigBehaviour.onConfigLoaded(
            map = featureMap,
            featureId = featureId
        )

        filterableFeatureConfigBehaviour.onConfigLoaded(
            featureId = featureId,
            filterByLabel = avStat?.filterByLabels,
            filterByRange = avStat?.filterByRange,
            fromValue = avStat?.fromValue,
            toValue = avStat?.toValue,
            selectedLabels = avStat?.labels
        )

        timeRangeConfigBehaviour.onConfigLoaded(
            sampleSize = avStat?.sampleSize,
            endingAt = avStat?.endDate
        )

        avStat?.let { this.averageTimeBetweenStat = it }
    }
}