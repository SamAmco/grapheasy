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

import com.androidplot.pie.Segment
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.IDataPoint
import com.samco.trackandgraph.base.database.dto.DataPoint
import com.samco.trackandgraph.base.database.dto.GraphOrStat
import com.samco.trackandgraph.base.database.dto.PieChart
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.di.IODispatcher
import com.samco.trackandgraph.functions.functions.DataClippingFunction
import com.samco.trackandgraph.graphstatview.GraphStatInitException
import com.samco.trackandgraph.graphstatview.factories.viewdto.IPieChartViewData
import com.samco.trackandgraph.graphstatview.factories.viewdto.IGraphStatViewData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PieChartDataFactory @Inject constructor(
    dataInteractor: DataInteractor,
    @IODispatcher ioDispatcher: CoroutineDispatcher
) : ViewDataFactory<PieChart, IPieChartViewData>(dataInteractor, ioDispatcher) {

    override suspend fun createViewData(
        graphOrStat: GraphOrStat,
        onDataSampled: (List<DataPoint>) -> Unit
    ): IPieChartViewData {
        val pieChart = dataInteractor.getPieChartByGraphStatId(graphOrStat.id)
            ?: return object : IPieChartViewData {
                override val state = IGraphStatViewData.State.ERROR
                override val graphOrStat = graphOrStat
                override val error = GraphStatInitException(R.string.graph_stat_view_not_found)
            }
        return createViewData(graphOrStat, pieChart, onDataSampled)
    }

    override suspend fun createViewData(
        graphOrStat: GraphOrStat,
        config: PieChart,
        onDataSampled: (List<DataPoint>) -> Unit
    ): IPieChartViewData {
        return try {
            val plottingData = tryGetPlottableDataForPieChart(config, onDataSampled)
                ?: return object : IPieChartViewData {
                    override val state = IGraphStatViewData.State.READY
                    override val graphOrStat = graphOrStat
                }
            val segments = getPieChartSegments(plottingData)
            val total = segments.sumOf { s -> s.value.toDouble() }
            val percentages = segments.map {
                Segment(it.title, (it.value.toDouble() / total) * 100f)
            }

            object : IPieChartViewData {
                override val segments = percentages
                override val state = IGraphStatViewData.State.READY
                override val graphOrStat = graphOrStat
            }
        } catch (throwable: Throwable) {
            object : IPieChartViewData {
                override val state = IGraphStatViewData.State.ERROR
                override val graphOrStat = graphOrStat
                override val error = throwable
            }
        }
    }

    private suspend fun tryGetPlottableDataForPieChart(
        pieChart: PieChart,
        onDataSampled: (List<DataPoint>) -> Unit
    ): List<IDataPoint>? {
        val feature = withContext(Dispatchers.IO) {
            dataInteractor.getFeatureById(pieChart.featureId)
        }
        val dataSample = DataClippingFunction(pieChart.endDate, pieChart.duration)
            .mapSample(dataInteractor.getDataSampleForFeatureId(feature.id))
        val dataPoints = dataSample
            .filter { it.label.isNotEmpty() }
            .toList()
        onDataSampled(dataSample.getRawDataPoints())
        return dataPoints.ifEmpty { null }
    }

    private fun getPieChartSegments(dataSample: List<IDataPoint>) =
        dataSample
            .groupingBy { dp -> dp.label }
            .eachCount()
            .map { b -> Segment(b.key, b.value) }

}