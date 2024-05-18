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

package com.samco.trackandgraph.graphstatview.factories

import com.samco.trackandgraph.base.database.dto.IDataPoint
import com.samco.trackandgraph.base.database.dto.AverageTimeBetweenStat
import com.samco.trackandgraph.base.database.dto.DataPoint
import com.samco.trackandgraph.base.database.dto.GraphOrStat
import com.samco.trackandgraph.base.database.sampling.DataSample
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.functions.functions.DataSampleFunction
import com.samco.trackandgraph.functions.functions.CompositeFunction
import com.samco.trackandgraph.functions.functions.DataClippingFunction
import com.samco.trackandgraph.functions.functions.FilterLabelFunction
import com.samco.trackandgraph.functions.functions.FilterValueFunction
import com.samco.trackandgraph.graphstatview.exceptions.GraphNotFoundException
import com.samco.trackandgraph.graphstatview.factories.viewdto.IAverageTimeBetweenViewData
import com.samco.trackandgraph.graphstatview.factories.viewdto.IGraphStatViewData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.threeten.bp.Duration
import javax.inject.Inject

class AverageTimeBetweenDataFactory @Inject constructor(
    dataInteractor: DataInteractor,
    @IODispatcher ioDispatcher: CoroutineDispatcher
) : ViewDataFactory<AverageTimeBetweenStat, IAverageTimeBetweenViewData>(
    dataInteractor,
    ioDispatcher
) {

    companion object {
        /**
         * Calculates the average duration between the timestamps of a set of data points. This is
         * simply the duration between first and last divided by the number of points minus 1.
         * Points must be passed in in order from newest to oldest
         */
        internal fun calculateAverageTimeBetween(
            dataPoints: List<IDataPoint>
        ): Double {
            if (dataPoints.size < 2) throw Exception("Don't call this method with less than 2 data points.")
            //The data points will be in order newest to oldest
            val last = dataPoints.first().timestamp
            val first = dataPoints.last().timestamp
            return Duration.between(first, last).toMillis()
                .toDouble() / (dataPoints.size - 1).toDouble()
        }
    }

    override suspend fun createViewData(
        graphOrStat: GraphOrStat,
        onDataSampled: (List<DataPoint>) -> Unit
    ): IAverageTimeBetweenViewData {
        val timeBetweenStat = dataInteractor.getAverageTimeBetweenStatByGraphStatId(graphOrStat.id)
            ?: return graphNotFound(graphOrStat)
        return createViewData(graphOrStat, timeBetweenStat, onDataSampled)
    }

    override suspend fun affectedBy(graphOrStatId: Long, featureId: Long): Boolean {
        return dataInteractor.getAverageTimeBetweenStatByGraphStatId(graphOrStatId)
            ?.featureId == featureId
    }

    override suspend fun createViewData(
        graphOrStat: GraphOrStat,
        config: AverageTimeBetweenStat,
        onDataSampled: (List<DataPoint>) -> Unit
    ): IAverageTimeBetweenViewData {
        return try {
            val dataSample = withContext(Dispatchers.IO) {
                getRelevantDataPoints(config, config.featureId)
            }
            val dataPoints = withContext(Dispatchers.IO) {
                dataSample.toList()
            }
            if (dataPoints.size < 2) return notEnoughData(graphOrStat)
            val averageMillis = withContext(Dispatchers.Default) {
                calculateAverageTimeBetween(dataPoints)
            }
            onDataSampled(dataSample.getRawDataPoints())
            dataSample.dispose()
            object : IAverageTimeBetweenViewData {
                override val state = IGraphStatViewData.State.READY
                override val graphOrStat = graphOrStat
                override val enoughData: Boolean = true
                override val averageMillis = averageMillis
            }
        } catch (throwable: Throwable) {
            object : IAverageTimeBetweenViewData {
                override val state = IGraphStatViewData.State.ERROR
                override val graphOrStat = graphOrStat
                override val error = throwable
            }
        }
    }

    private fun graphNotFound(graphOrStat: GraphOrStat) =
        object : IAverageTimeBetweenViewData {
            override val error = GraphNotFoundException()
            override val state = IGraphStatViewData.State.ERROR
            override val graphOrStat = graphOrStat
        }

    private fun notEnoughData(graphOrStat: GraphOrStat) =
        object : IAverageTimeBetweenViewData {
            override val state = IGraphStatViewData.State.READY
            override val enoughData = false
            override val graphOrStat = graphOrStat
        }

    private suspend fun getRelevantDataPoints(
        config: AverageTimeBetweenStat,
        featureId: Long
    ): DataSample {
        val dataSample = dataInteractor.getDataSampleForFeatureId(featureId)
        val filters = mutableListOf<DataSampleFunction>()
        if (config.filterByLabels) filters.add(FilterLabelFunction(config.labels.toSet()))
        if (config.filterByRange) filters.add(FilterValueFunction(config.fromValue, config.toValue))
        filters.add(DataClippingFunction(config.endDate.toOffsetDateTime(), config.sampleSize))
        return CompositeFunction(filters).mapSample(dataSample)
    }
}