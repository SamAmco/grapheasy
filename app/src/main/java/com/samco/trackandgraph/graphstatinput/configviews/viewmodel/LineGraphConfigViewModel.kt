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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.*
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.DefaultDispatcher
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.model.di.MainDispatcher
import com.samco.trackandgraph.graphstatinput.GraphStatConfigEvent
import com.samco.trackandgraph.graphstatinput.GraphStatConfigEvent.ValidationException
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.TimeRangeConfigBehaviour
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.TimeRangeConfigBehaviourImpl
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.YRangeConfigBehaviour
import com.samco.trackandgraph.graphstatinput.configviews.behaviour.YRangeConfigBehaviourImpl
import com.samco.trackandgraph.graphstatproviders.GraphStatInteractorProvider
import com.samco.trackandgraph.ui.dataVisColorGenerator
import com.samco.trackandgraph.ui.dataVisColorList
import com.samco.trackandgraph.ui.viewmodels.asValidatedDouble
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LineGraphConfigViewModel @Inject constructor(
    @IODispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val default: CoroutineDispatcher,
    @MainDispatcher private val ui: CoroutineDispatcher,
    gsiProvider: GraphStatInteractorProvider,
    dataInteractor: DataInteractor,
    private val timeRangeConfigBehaviour: TimeRangeConfigBehaviourImpl = TimeRangeConfigBehaviourImpl(),
    private val yRangeConfigBehaviour: YRangeConfigBehaviourImpl = YRangeConfigBehaviourImpl()
) : GraphStatConfigViewModelBase<GraphStatConfigEvent.ConfigData.LineGraphConfigData>(
    io,
    default,
    ui,
    gsiProvider,
    dataInteractor
), TimeRangeConfigBehaviour by timeRangeConfigBehaviour,
    YRangeConfigBehaviour by yRangeConfigBehaviour {

    init {
        timeRangeConfigBehaviour.initTimeRangeConfigBehaviour { onUpdate() }
        yRangeConfigBehaviour.initYRangeConfigBehaviour { onUpdate() }
    }

    inner class FeatureTextFields(
        name: String,
        offset: String = "0",
        scale: String = "1"
    ) {
        var name by mutableStateOf(TextFieldValue(name, TextRange(name.length)))
            private set
        var offset by mutableStateOf(TextFieldValue(offset, TextRange(offset.length)))
            private set
        var scale by mutableStateOf(TextFieldValue(scale, TextRange(scale.length)))
            private set

        fun updateName(name: TextFieldValue, triggerUpdate: Boolean = true) {
            this.name = name
            if (triggerUpdate) onUpdate()
        }

        fun updateOffset(offset: TextFieldValue) {
            this.offset = offset.asValidatedDouble()
            onUpdate()
        }

        fun updateScale(scale: TextFieldValue) {
            this.scale = scale.asValidatedDouble()
            onUpdate()
        }
    }

    data class FeatureSelectionIdentifier(
        val featureId: Long,
        val durationPlottingMode: DurationPlottingMode
    )

    data class FeaturePathViewData(
        val id: FeatureSelectionIdentifier,
        val path: String,
    )

    var featurePaths: List<FeaturePathViewData> by mutableStateOf(emptyList())

    var lineGraphFeatures by mutableStateOf(emptyList<LineGraphFeature>())
        private set

    private val isTimeBasedRange = snapshotFlow { lineGraphFeatures }
        .map { lgfs -> lgfs.any { it.durationPlottingMode == DurationPlottingMode.DURATION_IF_POSSIBLE } }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val featureTextFields = mutableListOf<FeatureTextFields>()

    private var lineGraph = LineGraphWithFeatures(
        id = 0L,
        graphStatId = 0L,
        features = listOf(),
        sampleSize = null,
        yRangeType = YRangeType.DYNAMIC,
        yFrom = 0.0,
        yTo = 1.0,
        endDate = GraphEndDate.Latest
    )

    override fun updateConfig() {
        lineGraph = lineGraph.copy(
            sampleSize = selectedDuration.temporalAmount,
            endDate = sampleEndingAt.asGraphEndDate(),
            yRangeType = yRangeType,
            features = lineGraphFeatures.mapIndexed { index, f ->
                f.copy(
                    name = featureTextFields[index].name.text,
                    offset = featureTextFields[index].offset.text.toDoubleOrNull() ?: 0.0,
                    scale = featureTextFields[index].scale.text.toDoubleOrNull() ?: 1.0
                )
            },
            yFrom = getYFrom(),
            yTo = getYTo()
        )
    }

    private fun getYFrom(): Double =
        if (isTimeBasedRange.value) yRangeFromDurationViewModel.getDurationAsDouble()
        else yRangeFrom.text.toDoubleOrNull() ?: 0.0

    private fun getYTo(): Double =
        if (isTimeBasedRange.value) yRangeToDurationViewModel.getDurationAsDouble()
        else yRangeTo.text.toDoubleOrNull() ?: 1.0


    override fun getConfig(): GraphStatConfigEvent.ConfigData.LineGraphConfigData {
        return GraphStatConfigEvent.ConfigData.LineGraphConfigData(lineGraph)
    }

    override suspend fun validate(): ValidationException? {
        if (lineGraph.features.isEmpty()) {
            return ValidationException(R.string.graph_stat_validation_no_line_graph_features)
        }
        lineGraph.features.forEach { f ->
            if (f.colorIndex !in dataVisColorList.indices) {
                return ValidationException(R.string.graph_stat_validation_unrecognised_color)
            }
        }
        if (lineGraph.features.any { it.durationPlottingMode == DurationPlottingMode.DURATION_IF_POSSIBLE }
            && !lineGraph.features.all { it.durationPlottingMode == DurationPlottingMode.DURATION_IF_POSSIBLE }) {
            return ValidationException(R.string.graph_stat_validation_mixed_time_value_line_graph_features)
        }
        if (lineGraph.yRangeType == YRangeType.FIXED && lineGraph.yFrom >= lineGraph.yTo) {
            return ValidationException(R.string.graph_stat_validation_bad_fixed_range)
        }
        return null
    }

    fun onAddLineGraphFeatureClicked() = viewModelScope.launch {
        val firstFeature = featurePathProvider.features.firstOrNull()
        val featureName = firstFeature?.name ?: ""
        val isDuration = firstFeature?.featureId?.let {
            featurePathProvider.getDataSampleProperties(it)?.isDuration
        } == true
        val durationPlottingMode =
            if (isDuration) DurationPlottingMode.DURATION_IF_POSSIBLE
            else DurationPlottingMode.NONE
        featureTextFields.add(FeatureTextFields(featureName))
        lineGraphFeatures = lineGraphFeatures.toMutableList().apply {
            add(
                LineGraphFeature(
                    id = 0L,
                    lineGraphId = -1L,
                    featureId = firstFeature?.featureId ?: -1L,
                    name = featureName,
                    colorIndex = (size * dataVisColorGenerator) % dataVisColorList.size,
                    averagingMode = LineGraphAveraginModes.NO_AVERAGING,
                    plottingMode = LineGraphPlottingModes.WHEN_TRACKED,
                    pointStyle = LineGraphPointStyle.NONE,
                    offset = 0.toDouble(),
                    scale = 1.toDouble(),
                    durationPlottingMode = durationPlottingMode
                )
            )
        }
        onUpdate()
    }

    private fun initFeaturePaths() {
        featurePaths = featurePathProvider.dataSourceDataAlphabetically()
            .flatMap {
                val id = it.feature.featureId
                val path = it.path
                if (it.dataProperties?.isDuration == true) sequenceOf(
                    FeaturePathViewData(
                        id = FeatureSelectionIdentifier(
                            featureId = id,
                            durationPlottingMode = DurationPlottingMode.DURATION_IF_POSSIBLE
                        ),
                        path = path,
                    ),
                    FeaturePathViewData(
                        id = FeatureSelectionIdentifier(
                            featureId = id,
                            durationPlottingMode = DurationPlottingMode.HOURS
                        ),
                        path = path
                    ),
                    FeaturePathViewData(
                        id = FeatureSelectionIdentifier(
                            featureId = id,
                            durationPlottingMode = DurationPlottingMode.MINUTES
                        ),
                        path = path
                    ),
                    FeaturePathViewData(
                        id = FeatureSelectionIdentifier(
                            featureId = id,
                            durationPlottingMode = DurationPlottingMode.SECONDS
                        ),
                        path = path
                    ),
                )
                else sequenceOf(
                    FeaturePathViewData(
                        id = FeatureSelectionIdentifier(
                            featureId = id,
                            durationPlottingMode = DurationPlottingMode.NONE
                        ),
                        path = path
                    )
                )
            }
    }

    override fun onDataLoaded(config: Any?) {

        val lgConfig = config as? LineGraphWithFeatures

        initFeaturePaths()

        timeRangeConfigBehaviour.onConfigLoaded(
            sampleSize = lgConfig?.sampleSize,
            endingAt = lgConfig?.endDate
        )

        yRangeConfigBehaviour.onConfigLoaded(
            yRangeType = lgConfig?.yRangeType,
            yFrom = lgConfig?.yFrom,
            yTo = lgConfig?.yTo,
            timeBasedRange = isTimeBasedRange
        )

        lgConfig?.let {
            lineGraph = it
            lineGraphFeatures = it.features
            it.features.forEach { lgf ->
                featureTextFields.add(
                    FeatureTextFields(
                        lgf.name,
                        lgf.offset.toString(),
                        lgf.scale.toString()
                    )
                )
            }
        }

        viewModelScope.launch {
            isTimeBasedRange.drop(1).collect {
                onUpdate()
            }
        }
    }

    fun removeLineGraphFeature(index: Int) {
        lineGraphFeatures = lineGraphFeatures.toMutableList().apply { removeAt(index) }
        featureTextFields.removeAt(index)
        onUpdate()
    }

    fun updateLineGraphFeature(index: Int, newLgf: LineGraphFeature) = viewModelScope.launch {
        val newFeatureName = featurePathProvider.features
            .firstOrNull { it.featureId == newLgf.featureId }?.name ?: ""
        val oldFeatureName = featurePathProvider.features
            .firstOrNull { it.featureId == lineGraphFeatures[index].featureId }?.name ?: ""

        lineGraphFeatures = lineGraphFeatures.toMutableList().apply {
            val textFields = featureTextFields[index]
            if (textFields.name.text == oldFeatureName) {
                textFields.updateName(
                    TextFieldValue(
                        newFeatureName,
                        TextRange(newFeatureName.length)
                    ),
                    triggerUpdate = false
                )
            }
            set(
                index, newLgf.copy(
                    name = textFields.name.text,
                    offset = textFields.offset.text.toDoubleOrNull() ?: 0.toDouble(),
                    scale = textFields.scale.text.toDoubleOrNull() ?: 1.toDouble()
                )
            )
        }
        onUpdate()
    }

    fun getTextFieldsFor(index: Int): FeatureTextFields {
        return featureTextFields[index]
    }
}