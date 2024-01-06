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

package com.samco.trackandgraph.base.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.room.withTransaction
import com.samco.trackandgraph.base.database.TrackAndGraphDatabase
import com.samco.trackandgraph.base.database.TrackAndGraphDatabaseDao
import com.samco.trackandgraph.base.database.dto.*
import com.samco.trackandgraph.base.database.sampling.DataSampler
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.service.ServiceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

internal class DataInteractorImpl @Inject constructor(
    private val database: TrackAndGraphDatabase,
    private val dao: TrackAndGraphDatabaseDao,
    @IODispatcher private val io: CoroutineDispatcher,
    private val trackerHelper: TrackerHelper,
    private val csvReadWriter: CSVReadWriter,
    private val alarmInteractor: AlarmInteractor,
    private val serviceManager: ServiceManager,
    private val dataSampler: DataSampler
) : DataInteractor, TrackerHelper by trackerHelper, DataSampler by dataSampler {

    private val dataUpdateEvents = MutableSharedFlow<DataUpdateType>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override suspend fun insertGroup(group: Group): Long = withContext(io) {
        dao.insertGroup(group.toEntity())
            .also { dataUpdateEvents.emit(DataUpdateType.GroupCreated) }
    }

    override suspend fun deleteGroup(id: Long) = withContext(io) {
        //Get all feature ids before we delete the group
        val allFeatureIdsBeforeDelete = dao.getAllFeaturesSync().map { it.id }.toSet()
        //Delete the group
        dao.deleteGroup(id)
        //Get all feature ids after deleting the group
        val allFeatureIdsAfterDelete = dao.getAllFeaturesSync().map { it.id }.toSet()
        val deletedFeatureIds = allFeatureIdsBeforeDelete.minus(allFeatureIdsAfterDelete)
        //Trigger a feature delete request for all deleted features
        deletedFeatureIds.forEach { serviceManager.requestWidgetsDisabledForFeatureId(it) }
        //Emit a data update event
        dataUpdateEvents.emit(DataUpdateType.GroupDeleted)
    }

    override suspend fun updateGroup(group: Group) = withContext(io) {
        //We need to ensure we're not attempting to move a group to its own child
        // as this would create an infinite loop
        val visited = mutableListOf(group.id)
        var currentParentId = group.parentGroupId
        while (currentParentId != null) {
            if (visited.contains(currentParentId)) throw IllegalArgumentException("Illegal group move detected")
            visited.add(currentParentId)
            currentParentId = dao.getGroupById(currentParentId).parentGroupId
        }

        dao.updateGroup(group.toEntity())
            .also { dataUpdateEvents.emit(DataUpdateType.GroupUpdated) }
    }

    override fun getAllGroups(): LiveData<List<Group>> {
        return Transformations.map(dao.getAllGroups()) { groups -> groups.map { it.toDto() } }
    }

    override suspend fun getAllGroupsSync(): List<Group> = withContext(io) {
        dao.getAllGroupsSync().map { it.toDto() }
    }

    override suspend fun getGroupById(id: Long): Group = withContext(io) {
        dao.getGroupById(id).toDto()
    }

    override fun getAllReminders(): LiveData<List<Reminder>> {
        return Transformations.map(dao.getAllReminders()) { reminders -> reminders.map { it.toDto() } }
    }

    override suspend fun getAllRemindersSync(): List<Reminder> = withContext(io) {
        dao.getAllRemindersSync().map { it.toDto() }
    }

    override suspend fun getFeaturesForGroupSync(groupId: Long): List<Feature> = withContext(io) {
        dao.getFeaturesForGroupSync(groupId).map { it.toDto() }
    }

    override suspend fun getFeatureById(featureId: Long): Feature? = withContext(io) {
        dao.getFeatureById(featureId)?.toDto()
    }

    override suspend fun insertTracker(tracker: Tracker): Long = withContext(io) {
        val id = trackerHelper.insertTracker(tracker)
        dataUpdateEvents.emit(DataUpdateType.TrackerCreated)
        return@withContext id
    }

    override suspend fun updateTracker(tracker: Tracker) = withContext(io) {
        trackerHelper.updateTracker(tracker)
        dataUpdateEvents.emit(DataUpdateType.TrackerUpdated)
        serviceManager.requestWidgetUpdatesForFeatureId(featureId = tracker.featureId)
    }

    override suspend fun updateTracker(
        oldTracker: Tracker,
        durationNumericConversionMode: TrackerHelper.DurationNumericConversionMode?,
        newName: String?,
        newType: DataType?,
        hasDefaultValue: Boolean?,
        defaultValue: Double?,
        defaultLabel: String?,
        featureDescription: String?,
        suggestionType: TrackerSuggestionType?,
        suggestionOrder: TrackerSuggestionOrder?
    ) = withContext(io) {
        trackerHelper.updateTracker(
            oldTracker = oldTracker,
            durationNumericConversionMode = durationNumericConversionMode,
            newName = newName,
            newType = newType,
            hasDefaultValue = hasDefaultValue,
            defaultValue = defaultValue,
            defaultLabel = defaultLabel,
            featureDescription = featureDescription,
            suggestionType = suggestionType,
            suggestionOrder = suggestionOrder
        ).also {
            serviceManager.requestWidgetUpdatesForFeatureId(featureId = oldTracker.featureId)
            dataUpdateEvents.emit(DataUpdateType.TrackerUpdated)
        }
    }

    override suspend fun updateDataPoints(
        trackerId: Long,
        whereValue: Double?,
        whereLabel: String?,
        toValue: Double?,
        toLabel: String?
    ) = withContext(io) {
        trackerHelper.updateDataPoints(
            trackerId = trackerId,
            whereValue = whereValue,
            whereLabel = whereLabel,
            toValue = toValue,
            toLabel = toLabel
        ).also {
            dao.getTrackerById(trackerId)?.featureId?.let {
                dataUpdateEvents.emit(DataUpdateType.DataPoint(it))
            }
        }
    }

    override suspend fun deleteFeature(featureId: Long) = withContext(io) {
        val isTracker = dao.getTrackerByFeatureId(featureId) != null
        dao.deleteFeature(featureId)
        serviceManager.requestWidgetsDisabledForFeatureId(featureId = featureId)
        if (isTracker) dataUpdateEvents.emit(DataUpdateType.TrackerDeleted)
        else dataUpdateEvents.emit(DataUpdateType.Function)
    }

    override suspend fun updateReminders(reminders: List<Reminder>) = withContext(io) {
        alarmInteractor.clearAlarms()
        dao.deleteReminders()
        reminders
            .map { it.toEntity() }
            .forEach { dao.insertReminder(it) }
        alarmInteractor.syncAlarms()
        dataUpdateEvents.emit(DataUpdateType.Reminder)
    }

    override suspend fun deleteDataPoint(dataPoint: DataPoint) = withContext(io) {
        dao.deleteDataPoint(dataPoint.toEntity())
            .also { dataUpdateEvents.emit(DataUpdateType.DataPoint(dataPoint.featureId)) }
    }

    override suspend fun deleteGraphOrStat(id: Long) = withContext(io) {
        dao.deleteGraphOrStat(id).also { dataUpdateEvents.emit(DataUpdateType.GraphOrStatDeleted) }
    }

    override suspend fun deleteGraphOrStat(graphOrStat: GraphOrStat) = withContext(io) {
        dao.deleteGraphOrStat(graphOrStat.toEntity())
            .also { dataUpdateEvents.emit(DataUpdateType.GraphOrStatDeleted) }
    }

    override suspend fun insertDataPoint(dataPoint: DataPoint): Long = withContext(io) {
        dao.insertDataPoint(dataPoint.toEntity())
            .also { dataUpdateEvents.emit(DataUpdateType.DataPoint(dataPoint.featureId)) }
    }

    override suspend fun insertDataPoints(dataPoints: List<DataPoint>) = withContext(io) {
        if (dataPoints.isEmpty()) return@withContext
        dao.insertDataPoints(dataPoints.map { it.toEntity() }).also {
            dataUpdateEvents.emit(DataUpdateType.DataPoint(dataPoints.first().featureId))
        }
    }

    override fun getDataUpdateEvents(): SharedFlow<DataUpdateType> = dataUpdateEvents

    override suspend fun getGraphStatById(graphStatId: Long): GraphOrStat = withContext(io) {
        dao.getGraphStatById(graphStatId).toDto()
    }

    override suspend fun tryGetGraphStatById(graphStatId: Long): GraphOrStat? = withContext(io) {
        dao.tryGetGraphStatById(graphStatId)?.toDto()
    }

    override suspend fun getLineGraphByGraphStatId(graphStatId: Long): LineGraphWithFeatures? =
        withContext(io) {
            dao.getLineGraphByGraphStatId(graphStatId)?.toDto()
        }

    override suspend fun getPieChartByGraphStatId(graphStatId: Long): PieChart? = withContext(io) {
        dao.getPieChartByGraphStatId(graphStatId)?.toDto()
    }

    override suspend fun getAverageTimeBetweenStatByGraphStatId(graphStatId: Long): AverageTimeBetweenStat? =
        withContext(io) {
            dao.getAverageTimeBetweenStatByGraphStatId(graphStatId)?.toDto()
        }

    override suspend fun getGraphsAndStatsByGroupIdSync(groupId: Long): List<GraphOrStat> =
        withContext(io) {
            dao.getGraphsAndStatsByGroupIdSync(groupId).map { it.toDto() }
        }

    override suspend fun getAllGraphStatsSync(): List<GraphOrStat> = withContext(io) {
        dao.getAllGraphStatsSync().map { it.toDto() }
    }

    override fun getAllDisplayNotes(): Flow<List<DisplayNote>> {
        return dao.getAllDisplayNotes().map { notes ->
            notes.map { it.toDto() }
        }
    }

    override suspend fun removeNote(timestamp: OffsetDateTime, trackerId: Long) {
        withContext(io) {
            dao.getTrackerById(trackerId)?.featureId?.let { featureId ->
                dao.removeNote(timestamp.toInstant().toEpochMilli(), featureId).also {
                    dataUpdateEvents.emit(DataUpdateType.DataPoint(featureId))
                }
            }
        }
    }

    override suspend fun deleteGlobalNote(note: GlobalNote) = withContext(io) {
        dao.deleteGlobalNote(note.toEntity())
            .also { dataUpdateEvents.emit(DataUpdateType.GlobalNote) }
    }

    override suspend fun insertGlobalNote(note: GlobalNote): Long = withContext(io) {
        dao.insertGlobalNote(note.toEntity())
            .also { dataUpdateEvents.emit(DataUpdateType.GlobalNote) }
    }

    override suspend fun getGlobalNoteByTimeSync(timestamp: OffsetDateTime?): GlobalNote? =
        withContext(io) {
            timestamp?.let {
                dao.getGlobalNoteByTimeSync(it.toInstant().toEpochMilli())?.toDto()
            }
        }

    override suspend fun getAllGlobalNotesSync(): List<GlobalNote> = withContext(io) {
        dao.getAllGlobalNotesSync().map { it.toDto() }
    }

    private fun duplicateGraphOrStat(graphOrStat: GraphOrStat) =
        dao.insertGraphOrStat(graphOrStat.copy(id = 0L, displayIndex = 0).toEntity())

    private suspend fun <R> performAtomicUpdate(
        updateType: DataUpdateType? = null,
        block: suspend () -> R
    ) = withContext(io) {
        database
            .withTransaction { block() }
            .also { updateType?.let { dataUpdateEvents.emit(it) } }
    }

    private suspend fun shiftUpGroupChildIndexes(groupId: Long) =
        performAtomicUpdate(DataUpdateType.DisplayIndex) {
            //Update features
            dao.getFeaturesForGroupSync(groupId).let { features ->
                dao.updateFeatures(features.map { it.copy(displayIndex = it.displayIndex + 1) })
            }

            //Update graphs
            dao.getGraphsAndStatsByGroupIdSync(groupId).let { graphs ->
                dao.updateGraphStats(graphs.map { it.copy(displayIndex = it.displayIndex + 1) })
            }

            //Update groups
            dao.getGroupsForGroupSync(groupId).let { groups ->
                dao.updateGroups(groups.map { it.copy(displayIndex = it.displayIndex + 1) })
            }
        }

    override suspend fun duplicateLineGraph(graphOrStat: GraphOrStat): Long? =
        performAtomicUpdate {
            shiftUpGroupChildIndexes(graphOrStat.groupId)
            val newGraphStat = duplicateGraphOrStat(graphOrStat)
            dao.getLineGraphByGraphStatId(graphOrStat.id)?.let {
                val copy = dao.insertLineGraph(
                    it.toLineGraph().copy(id = 0L, graphStatId = newGraphStat)
                )
                dao.insertLineGraphFeatures(it.features.map { f ->
                    f.copy(id = 0L, lineGraphId = copy)
                })
                copy
            }.also { dataUpdateEvents.emit(DataUpdateType.GraphOrStatCreated(newGraphStat)) }
        }

    override suspend fun duplicatePieChart(graphOrStat: GraphOrStat): Long? =
        performAtomicUpdate {
            shiftUpGroupChildIndexes(graphOrStat.groupId)
            val newGraphStat = duplicateGraphOrStat(graphOrStat)
            dao.getPieChartByGraphStatId(graphOrStat.id)?.let {
                dao.insertPieChart(it.copy(id = 0L, graphStatId = newGraphStat))
            }.also { dataUpdateEvents.emit(DataUpdateType.GraphOrStatCreated(newGraphStat)) }
        }

    override suspend fun duplicateAverageTimeBetweenStat(graphOrStat: GraphOrStat): Long? =
        performAtomicUpdate {
            shiftUpGroupChildIndexes(graphOrStat.groupId)
            val newGraphStat = duplicateGraphOrStat(graphOrStat)
            dao.getAverageTimeBetweenStatByGraphStatId(graphOrStat.id)?.let {
                dao.insertAverageTimeBetweenStat(it.copy(id = 0L, graphStatId = newGraphStat))
            }.also { dataUpdateEvents.emit(DataUpdateType.GraphOrStatCreated(newGraphStat)) }
        }

    override suspend fun duplicateTimeHistogram(graphOrStat: GraphOrStat): Long? =
        performAtomicUpdate {
            shiftUpGroupChildIndexes(graphOrStat.groupId)
            val newGraphStat = duplicateGraphOrStat(graphOrStat)
            dao.getTimeHistogramByGraphStatId(graphOrStat.id)?.let {
                dao.insertTimeHistogram(it.copy(id = 0L, graphStatId = newGraphStat))
            }.also { dataUpdateEvents.emit(DataUpdateType.GraphOrStatCreated(newGraphStat)) }
        }

    override suspend fun duplicateLastValueStat(graphOrStat: GraphOrStat): Long? =
        performAtomicUpdate {
            shiftUpGroupChildIndexes(graphOrStat.groupId)
            val newGraphStat = duplicateGraphOrStat(graphOrStat)
            dao.getLastValueStatByGraphStatId(graphOrStat.id)?.let {
                dao.insertLastValueStat(it.copy(id = 0L, graphStatId = newGraphStat))
            }.also { dataUpdateEvents.emit(DataUpdateType.GraphOrStatCreated(newGraphStat)) }
        }

    override suspend fun duplicateBarChart(graphOrStat: GraphOrStat): Long? =
        performAtomicUpdate {
            shiftUpGroupChildIndexes(graphOrStat.groupId)
            val newGraphStat = duplicateGraphOrStat(graphOrStat)
            dao.getBarChartByGraphStatId(graphOrStat.id)?.let {
                dao.insertBarChart(it.copy(id = 0L, graphStatId = newGraphStat))
            }.also { dataUpdateEvents.emit(DataUpdateType.GraphOrStatCreated(newGraphStat)) }
        }

    private fun insertGraphStat(graphOrStat: GraphOrStat) =
        dao.insertGraphOrStat(graphOrStat.copy(id = 0L, displayIndex = 0).toEntity())

    override suspend fun insertLineGraph(
        graphOrStat: GraphOrStat,
        lineGraph: LineGraphWithFeatures
    ): Long = performAtomicUpdate(DataUpdateType.GraphOrStatCreated(graphOrStat.id)) {
        shiftUpGroupChildIndexes(graphOrStat.groupId)
        val id = insertGraphStat(graphOrStat)
        val lineGraphId =
            dao.insertLineGraph(lineGraph.toLineGraph().copy(graphStatId = id).toEntity())
        val features = lineGraph.features.map { it.copy(lineGraphId = lineGraphId).toEntity() }
        dao.insertLineGraphFeatures(features)
        lineGraphId
    }

    override suspend fun insertPieChart(graphOrStat: GraphOrStat, pieChart: PieChart): Long =
        performAtomicUpdate(DataUpdateType.GraphOrStatCreated(graphOrStat.id)) {
            shiftUpGroupChildIndexes(graphOrStat.groupId)
            val id = insertGraphStat(graphOrStat)
            dao.insertPieChart(pieChart.copy(graphStatId = id).toEntity())
        }

    override suspend fun insertAverageTimeBetweenStat(
        graphOrStat: GraphOrStat,
        averageTimeBetweenStat: AverageTimeBetweenStat
    ): Long = performAtomicUpdate(DataUpdateType.GraphOrStatCreated(graphOrStat.id)) {
        shiftUpGroupChildIndexes(graphOrStat.groupId)
        val id = insertGraphStat(graphOrStat)
        dao.insertAverageTimeBetweenStat(
            averageTimeBetweenStat.copy(graphStatId = id).toEntity()
        )
    }

    override suspend fun insertTimeHistogram(
        graphOrStat: GraphOrStat,
        timeHistogram: TimeHistogram
    ) = performAtomicUpdate(DataUpdateType.GraphOrStatCreated(graphOrStat.id)) {
        shiftUpGroupChildIndexes(graphOrStat.groupId)
        val id = insertGraphStat(graphOrStat)
        dao.insertTimeHistogram(timeHistogram.copy(graphStatId = id).toEntity())
    }

    override suspend fun insertLastValueStat(
        graphOrStat: GraphOrStat,
        config: LastValueStat
    ): Long = performAtomicUpdate(DataUpdateType.GraphOrStatCreated(graphOrStat.id)) {
        shiftUpGroupChildIndexes(graphOrStat.groupId)
        val id = insertGraphStat(graphOrStat)
        dao.insertLastValueStat(config.copy(graphStatId = id).toEntity())
    }

    override suspend fun insertBarChart(graphOrStat: GraphOrStat, barChart: BarChart): Long =
        performAtomicUpdate(DataUpdateType.GraphOrStatCreated(graphOrStat.id)) {
            shiftUpGroupChildIndexes(graphOrStat.groupId)
            val id = insertGraphStat(graphOrStat)
            dao.insertBarChart(barChart.copy(graphStatId = id).toEntity())
        }

    override suspend fun updatePieChart(graphOrStat: GraphOrStat, pieChart: PieChart) =
        performAtomicUpdate(DataUpdateType.GraphOrStatUpdated(graphOrStat.id)) {
            dao.updateGraphOrStat(graphOrStat.toEntity())
            dao.updatePieChart(pieChart.toEntity())
        }

    override suspend fun updateAverageTimeBetweenStat(
        graphOrStat: GraphOrStat,
        averageTimeBetweenStat: AverageTimeBetweenStat
    ) = performAtomicUpdate(DataUpdateType.GraphOrStatUpdated(graphOrStat.id)) {
        dao.updateGraphOrStat(graphOrStat.toEntity())
        dao.updateAverageTimeBetweenStat(averageTimeBetweenStat.toEntity())
    }

    override suspend fun updateLineGraph(
        graphOrStat: GraphOrStat,
        lineGraph: LineGraphWithFeatures
    ) = performAtomicUpdate(DataUpdateType.GraphOrStatUpdated(graphOrStat.id)) {
        dao.updateGraphOrStat(graphOrStat.toEntity())
        dao.updateLineGraph(lineGraph.toLineGraph().toEntity())
        dao.deleteFeaturesForLineGraph(lineGraph.id)
        dao.insertLineGraphFeatures(lineGraph.features.map {
            it.copy(lineGraphId = lineGraph.id).toEntity()
        })
    }

    override suspend fun updateGraphOrStat(graphOrStat: GraphOrStat) =
        performAtomicUpdate(DataUpdateType.GraphOrStatUpdated(graphOrStat.id)) {
            dao.updateGraphOrStat(graphOrStat.toEntity())
        }

    override suspend fun updateLastValueStat(
        graphOrStat: GraphOrStat,
        config: LastValueStat
    ) = performAtomicUpdate(DataUpdateType.GraphOrStatUpdated(graphOrStat.id)) {
        dao.updateGraphOrStat(graphOrStat.toEntity())
        dao.updateLastValueStat(config.toEntity())
    }

    override suspend fun updateBarChart(
        graphOrStat: GraphOrStat,
        barChart: BarChart
    ) = performAtomicUpdate(DataUpdateType.GraphOrStatUpdated(graphOrStat.id)) {
        dao.updateGraphOrStat(graphOrStat.toEntity())
        dao.updateBarChart(barChart.toEntity())
    }

    override suspend fun updateTimeHistogram(
        graphOrStat: GraphOrStat,
        timeHistogram: TimeHistogram
    ) = performAtomicUpdate(DataUpdateType.GraphOrStatUpdated(graphOrStat.id)) {
        dao.updateGraphOrStat(graphOrStat.toEntity())
        dao.updateTimeHistogram(timeHistogram.toEntity())
    }

    override suspend fun updateGroupChildOrder(groupId: Long, children: List<GroupChild>) =
        performAtomicUpdate(DataUpdateType.DisplayIndex) {
            //Update trackers
            dao.getTrackersForGroupSync(groupId).let { features ->
                val updates = features.map { feature ->
                    val newDisplayIndex = children.indexOfFirst {
                        it.type == GroupChildType.TRACKER && it.id == feature.id
                    }
                    feature.copy(displayIndex = newDisplayIndex)
                }.map { it.toFeatureEntity() }
                dao.updateFeatures(updates)
            }

            //Update graphs
            dao.getGraphsAndStatsByGroupIdSync(groupId).let { graphs ->
                val updates = graphs.map { graph ->
                    val newDisplayIndex = children.indexOfFirst {
                        it.type == GroupChildType.GRAPH && it.id == graph.id
                    }
                    graph.copy(displayIndex = newDisplayIndex)
                }
                dao.updateGraphStats(updates)
            }

            //Update groups
            dao.getGroupsForGroupSync(groupId).let { groups ->
                val updates = groups.map { group ->
                    val newDisplayIndex = children.indexOfFirst {
                        it.type == GroupChildType.GROUP && it.id == group.id
                    }
                    group.copy(displayIndex = newDisplayIndex)
                }
                dao.updateGroups(updates)
            }
        }

    override suspend fun getTimeHistogramByGraphStatId(graphStatId: Long): TimeHistogram? =
        withContext(io) {
            dao.getTimeHistogramByGraphStatId(graphStatId)?.toDto()
        }

    override suspend fun getLastValueStatByGraphStatId(graphOrStatId: Long): LastValueStat? =
        withContext(io) {
            dao.getLastValueStatByGraphStatId(graphOrStatId)?.toDto()
        }

    override suspend fun getBarChartByGraphStatId(graphStatId: Long): BarChart? =
        withContext(io) {
            dao.getBarChartByGraphStatId(graphStatId)?.toDto()
        }

    override suspend fun getGroupsForGroupSync(id: Long): List<Group> = withContext(io) {
        dao.getGroupsForGroupSync(id).map { it.toDto() }
    }

    override suspend fun writeFeaturesToCSV(outStream: OutputStream, featureIds: List<Long>) =
        withContext(io) {
            val featureMap = featureIds
                .mapNotNull { getFeatureById(it) }
                .associateWith { getDataSampleForFeatureId(it.featureId) }
            try {
                csvReadWriter.writeFeaturesToCSV(outStream, featureMap)
            } finally {
                featureMap.values.forEach { it.dispose() }
            }
        }

    override suspend fun readFeaturesFromCSV(inputStream: InputStream, trackGroupId: Long) =
        withContext(io) {
            try {
                csvReadWriter.readFeaturesFromCSV(inputStream, trackGroupId)
            } finally {
                dataUpdateEvents.emit(DataUpdateType.TrackerCreated)
            }
        }

    override suspend fun playTimerForTracker(trackerId: Long): Long? {
        return trackerHelper.playTimerForTracker(trackerId)?.also {
            serviceManager.startTimerNotificationService()
            serviceManager.requestWidgetUpdatesForFeatureId(it)
            dataUpdateEvents.emit(DataUpdateType.TrackerUpdated)
        }
    }

    override suspend fun stopTimerForTracker(trackerId: Long): Duration? =
        trackerHelper.getTrackerById(trackerId)?.let { tracker ->
            trackerHelper.stopTimerForTracker(trackerId).also {
                serviceManager.requestWidgetUpdatesForFeatureId(tracker.featureId)
                dataUpdateEvents.emit(DataUpdateType.TrackerUpdated)
            }
        }

    override suspend fun getFunctionById(functionId: Long): FunctionDto? = withContext(io) {
        dao.getFunctionById(functionId)?.let { functionEntity ->
            dao.getFeatureById(functionEntity.featureId)?.let { feature ->
                FunctionDto.fromEntities(functionEntity, feature)
            }
        }
    }

    override suspend fun updateFunction(function: FunctionDto) = withContext(io) {
        dao.updateFunction(function.toEntity()).also {
            dataUpdateEvents.emit(DataUpdateType.Function)
        }
    }

    override suspend fun insertFunction(function: FunctionDto) = withContext(io) {
        dao.createFunction(function.toEntity())
            .also { dataUpdateEvents.emit(DataUpdateType.Function) }
    }

    override suspend fun getAllFeaturesSync(): List<Feature> = withContext(io) {
        dao.getAllFeaturesSync().map { it.toDto() }
    }
}