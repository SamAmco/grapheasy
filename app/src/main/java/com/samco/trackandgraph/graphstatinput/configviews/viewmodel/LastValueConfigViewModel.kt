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

import androidx.lifecycle.viewModelScope
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.GraphEndDate
import com.samco.trackandgraph.base.database.dto.LastValueStat
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.DefaultDispatcher
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.model.di.MainDispatcher
import com.samco.trackandgraph.graphstatinput.GraphStatConfigEvent
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.*
import com.samco.trackandgraph.graphstatproviders.GraphStatInteractorProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@HiltViewModel
class LastValueConfigViewModel @Inject constructor(
    @IODispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val default: CoroutineDispatcher,
    @MainDispatcher private val ui: CoroutineDispatcher,
    gsiProvider: GraphStatInteractorProvider,
    dataInteractor: DataInteractor,
    private val endingAtConfigBehaviour: TimeRangeConfigBehaviourImpl = TimeRangeConfigBehaviourImpl(),
    private val singleFeatureConfigBehaviour: SingleFeatureConfigBehaviourImpl = SingleFeatureConfigBehaviourImpl(),
    private val filterableFeatureConfigBehaviour: FilterableFeatureConfigBehaviourImpl = FilterableFeatureConfigBehaviourImpl()
) : GraphStatConfigViewModelBase<GraphStatConfigEvent.ConfigData.LastValueConfigData>(
    io,
    default,
    ui,
    gsiProvider,
    dataInteractor
),
    EndingAtConfigBehaviour by endingAtConfigBehaviour,
    FilterableFeatureConfigBehaviour by filterableFeatureConfigBehaviour,
    SingleFeatureConfigBehaviour by singleFeatureConfigBehaviour {

    init {
        endingAtConfigBehaviour.initTimeRangeConfigBehaviour { onUpdate() }
        filterableFeatureConfigBehaviour.initFilterableFeatureConfigBehaviour(
            onUpdate = { onUpdate() },
            io = io,
            ui = ui,
            coroutineScope = viewModelScope,
            dataInteractor = dataInteractor
        )
        singleFeatureConfigBehaviour.initSingleFeatureConfigBehaviour(
            onUpdate = { onUpdate() },
            featureChangeCallback = {
                filterableFeatureConfigBehaviour.onFeatureIdUpdated(it)
            }
        )
    }

    private var lastValueStat = LastValueStat(
        id = 0,
        graphStatId = 0,
        featureId = -1L,
        endDate = GraphEndDate.Latest,
        fromValue = 0.0,
        toValue = 0.0,
        labels = emptyList(),
        filterByRange = false,
        filterByLabels = false
    )

    override fun updateConfig() {
        lastValueStat = lastValueStat.copy(
            featureId = singleFeatureConfigBehaviour.featureId ?: -1L,
            endDate = endingAtConfigBehaviour.sampleEndingAt.asGraphEndDate(),
            fromValue = filterableFeatureConfigBehaviour.fromValue.text.toDoubleOrNull() ?: 0.0,
            toValue = filterableFeatureConfigBehaviour.toValue.text.toDoubleOrNull() ?: 1.0,
            labels = filterableFeatureConfigBehaviour.selectedLabels,
            filterByRange = filterableFeatureConfigBehaviour.filterByRange,
            filterByLabels = filterableFeatureConfigBehaviour.filterByLabel
        )
    }

    override fun getConfig(): GraphStatConfigEvent.ConfigData.LastValueConfigData {
        return GraphStatConfigEvent.ConfigData.LastValueConfigData(lastValueStat)
    }

    override suspend fun validate(): GraphStatConfigEvent.ValidationException? {
        if (singleFeatureConfigBehaviour.featureId == -1L)
            return GraphStatConfigEvent.ValidationException(R.string.graph_stat_validation_no_line_graph_features)
        if (lastValueStat.filterByRange && (lastValueStat.fromValue > lastValueStat.toValue))
            return GraphStatConfigEvent.ValidationException(R.string.graph_stat_validation_invalid_value_stat_from_to)
        return null
    }

    override fun onDataLoaded(config: Any?) {

        val lvStat = config as? LastValueStat

        val featureMap = featurePathProvider.sortedFeatureMap()
        val featureId = lvStat?.featureId ?: featureMap.keys.first()

        singleFeatureConfigBehaviour.onConfigLoaded(
            map = featureMap,
            featureId = featureId
        )

        filterableFeatureConfigBehaviour.onConfigLoaded(
            featureId = featureId,
            filterByLabel = lvStat?.filterByLabels,
            filterByRange = lvStat?.filterByRange,
            fromValue = lvStat?.fromValue,
            toValue = lvStat?.toValue,
            selectedLabels = lvStat?.labels
        )

        endingAtConfigBehaviour.onConfigLoaded(
            sampleSize = null,
            endingAt = lvStat?.endDate
        )

        lvStat?.let { this.lastValueStat = it }
    }
}