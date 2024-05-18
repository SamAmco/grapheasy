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
@file:OptIn(FlowPreview::class)

package com.samco.trackandgraph.featurehistory

import androidx.lifecycle.*
import com.samco.trackandgraph.base.database.dto.DataPoint
import com.samco.trackandgraph.base.database.dto.Feature
import com.samco.trackandgraph.base.database.dto.Tracker
import com.samco.trackandgraph.base.database.sampling.DataSampleProperties
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.model.di.MainDispatcher
import com.samco.trackandgraph.ui.compose.ui.Datable
import com.samco.trackandgraph.ui.compose.ui.DateDisplayResolution
import com.samco.trackandgraph.ui.compose.ui.DateScrollData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import javax.inject.Inject

data class DataPointInfo(
    override val date: OffsetDateTime,
    val featureId: Long,
    val value: Double,
    val label: String,
    val note: String,
) : Datable {
    fun toDataPoint() = DataPoint(
        timestamp = date,
        featureId = featureId,
        value = value,
        label = label,
        note = note
    )
}

interface FeatureHistoryNavigationViewModel {
    fun initViewModel(featureId: Long)

    fun showUpdateAllDialog()
}

//TODO we should probably add a loading state for this when we're first loading the data
interface FeatureHistoryViewModel : UpdateDialogViewModel {
    val tracker: LiveData<Tracker?>
    val dateScrollData: LiveData<DateScrollData<DataPointInfo>>
    val showFeatureInfo: LiveData<Feature?>
    val showDataPointInfo: LiveData<DataPointInfo?>
    val showDeleteConfirmDialog: LiveData<Boolean>
    val showUpdateDialog: LiveData<Boolean>

    fun onDeleteClicked(dataPoint: DataPointInfo)
    fun onDeleteConfirmed()
    fun onDeleteDismissed()
    fun onDataPointClicked(dataPoint: DataPointInfo)
    fun onDismissDataPoint()
    fun onShowFeatureInfo()
    fun onHideFeatureInfo()
}

@HiltViewModel
class FeatureHistoryViewModelImpl @Inject constructor(
    private val dataInteractor: DataInteractor,
    @IODispatcher private val io: CoroutineDispatcher,
    @MainDispatcher private val ui: CoroutineDispatcher
) : UpdateDialogViewModelImpl(),
    FeatureHistoryViewModel,
    FeatureHistoryNavigationViewModel {
    private val featureIdFlow = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)

    private data class RawData(
        val dataSampleProperties: DataSampleProperties,
        val dataPoints: List<DataPointInfo>
    )

    private val dataUpdates = dataInteractor
        .getDataUpdateEvents()
        .map { }
        .debounce(10)
        .onStart { emit(Unit) }

    private val dataSample =
        combine(featureIdFlow, dataUpdates) { id, _ -> id }
            .map { featureId ->
                val dataSample = dataInteractor.getDataSampleForFeatureId(featureId)
                val answer = RawData(
                    dataSample.dataSampleProperties,
                    dataSample.getAllRawDataPoints()
                        .sortedByDescending { it.timestamp.toEpochSecond() }
                        .map { it.toDataPointInfo() }
                )
                dataSample.dispose()
                return@map answer
            }
            .flowOn(io)

    override val isDuration: LiveData<Boolean> = dataSample.map {
        it.dataSampleProperties.isDuration
    }.asLiveData(viewModelScope.coroutineContext)

    override val dateScrollData: LiveData<DateScrollData<DataPointInfo>> = dataSample
        .filter { it.dataPoints.isNotEmpty() }
        .map { it.dataPoints }
        .map { dataPoints ->
            val range = Duration
                .between(dataPoints.last().date, dataPoints.first().date)
                .abs()

            val dateDisplayResolution = when {
                range.toDays() > 365 -> DateDisplayResolution.MONTH_YEAR
                else -> DateDisplayResolution.MONTH_DAY
            }

            DateScrollData(
                dateDisplayResolution = dateDisplayResolution,
                items = dataPoints
            )
        }.asLiveData(viewModelScope.coroutineContext)

    private val showFeatureInfoFlow = MutableStateFlow(false)

    override val showFeatureInfo: LiveData<Feature?> = combine(
        showFeatureInfoFlow,
        featureIdFlow.map { dataInteractor.getFeatureById(it) }) { show, feature ->
        if (show) feature else null
    }.asLiveData(viewModelScope.coroutineContext)

    override val showDataPointInfo = MutableLiveData<DataPointInfo?>(null)

    private val confirmDeleteDataPoint = MutableStateFlow<DataPointInfo?>(null)

    override val showDeleteConfirmDialog = confirmDeleteDataPoint
        .map { it != null }
        .asLiveData(viewModelScope.coroutineContext)

    override val showUpdateDialog = MutableLiveData(false)

    private val trackerFlow: StateFlow<Tracker?> = featureIdFlow
        .map { dataInteractor.getTrackerByFeatureId(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override val tracker: LiveData<Tracker?> = trackerFlow
        .asLiveData(viewModelScope.coroutineContext)

    private var featureId: Long? = null

    override fun initViewModel(featureId: Long) {
        if (this.featureId != null) return
        this.featureId = featureId
        viewModelScope.launch(io) { featureIdFlow.emit(featureId) }
    }

    override fun onDeleteClicked(dataPoint: DataPointInfo) {
        confirmDeleteDataPoint.value = dataPoint
    }

    override fun onDeleteConfirmed() {
        viewModelScope.launch(io) {
            confirmDeleteDataPoint.value?.let {
                dataInteractor.deleteDataPoint(it.toDataPoint())
            }
            confirmDeleteDataPoint.value = null
        }
    }

    override fun onDeleteDismissed() {
        confirmDeleteDataPoint.value = null
    }

    override fun onDataPointClicked(dataPoint: DataPointInfo) {
        showFeatureInfoFlow.value = false
        showDataPointInfo.value = dataPoint
    }

    override fun onDismissDataPoint() {
        showDataPointInfo.value = null
    }

    override fun onShowFeatureInfo() {
        showDataPointInfo.value = null
        showFeatureInfoFlow.value = true
    }

    override fun onHideFeatureInfo() {
        showFeatureInfoFlow.value = false
    }

    override val isUpdating = MutableLiveData(false)

    override fun showUpdateAllDialog() {
        showUpdateDialog.value = true
    }

    override fun onConfirmUpdateWarning() {
        showUpdateWarning.value = false
        showUpdateDialog.value = false
        viewModelScope.launch(io) {
            withContext(ui) { isUpdating.value = true }
            trackerFlow.value?.let {
                dataInteractor.updateDataPoints(
                    trackerId = it.id,
                    whereValue = getWhereValueDouble(),
                    whereLabel = getWhereLabelString(),
                    toValue = getToValueDouble(),
                    toLabel = getToLabelString()
                )
            }
            withContext(ui) { isUpdating.value = false }
        }
    }

    override fun onCancelUpdate() {
        showUpdateDialog.value = false
        showUpdateWarning.value = false
    }

    private fun DataPoint.toDataPointInfo() = DataPointInfo(
        date = timestamp,
        featureId = featureId,
        value = value,
        label = label,
        note = note,
    )
}