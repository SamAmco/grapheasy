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

import com.androidplot.Region
import com.androidplot.util.SeriesUtils
import com.androidplot.xy.FastXYSeries
import com.androidplot.xy.RectRegion
import com.androidplot.xy.StepMode
import com.samco.trackandgraph.R
import com.samco.trackandgraph.database.TrackAndGraphDatabaseDao
import com.samco.trackandgraph.database.dto.LineGraphWithFeatures
import com.samco.trackandgraph.database.dto.YRangeType
import com.samco.trackandgraph.database.entity.*
import com.samco.trackandgraph.graphstatview.GraphStatInitException
import com.samco.trackandgraph.graphstatview.factories.viewdto.IGraphStatViewData
import com.samco.trackandgraph.graphstatview.factories.viewdto.ILineGraphViewData
import com.samco.trackandgraph.functionslib.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import kotlin.math.abs

class LineGraphDataFactory : ViewDataFactory<LineGraphWithFeatures, ILineGraphViewData>() {
    companion object {
        val instance = LineGraphDataFactory()
    }

    override suspend fun createViewData(
        dataSource: TrackAndGraphDatabaseDao,
        graphOrStat: GraphOrStat,
        config: LineGraphWithFeatures,
        onDataSampled: (List<DataPointInterface>) -> Unit
    ): ILineGraphViewData {
        val endTime = config.endDate ?: OffsetDateTime.now()
        val allReferencedDataPoints = mutableListOf<DataPointInterface>()
        val plottableData = generatePlottingData(
            dataSource,
            config,
            allReferencedDataPoints,
            endTime
        )
        val hasPlottableData = plottableData.any { kvp -> kvp.value != null }

        val durationBasedRange =
            config.features.any { f -> f.durationPlottingMode == DurationPlottingMode.DURATION_IF_POSSIBLE }
        val (bounds, yAxisParameters) = getYAxisParameters(
            config,
            plottableData.values,
            durationBasedRange
        )
        //val bounds = getBounds(config, plottableData.values)
        //val yAxisParameters = getYAxisParameters(bounds, durationBasedRange)

        onDataSampled(allReferencedDataPoints)

        return object : ILineGraphViewData {
            override val durationBasedRange: Boolean
                get() = durationBasedRange
            override val yRangeType: YRangeType
                get() = config.yRangeType
            override val bounds: RectRegion
                get() = bounds
            override val hasPlottableData: Boolean
                get() = hasPlottableData
            override val endTime: OffsetDateTime
                get() = endTime
            override val plottableData: Map<LineGraphFeature, FastXYSeries?>
                get() = plottableData
            override val state: IGraphStatViewData.State
                get() = IGraphStatViewData.State.READY
            override val graphOrStat: GraphOrStat
                get() = graphOrStat
            override val yAxisRangeParameters: Pair<StepMode, Double>
                get() = yAxisParameters
        }
    }

    override suspend fun createViewData(
        dataSource: TrackAndGraphDatabaseDao,
        graphOrStat: GraphOrStat,
        onDataSampled: (List<DataPointInterface>) -> Unit
    ): ILineGraphViewData {
        val lineGraph = dataSource.getLineGraphByGraphStatId(graphOrStat.id)
            ?: return object : ILineGraphViewData {
                override val state: IGraphStatViewData.State
                    get() = IGraphStatViewData.State.ERROR
                override val graphOrStat: GraphOrStat
                    get() = graphOrStat
                override val error: GraphStatInitException?
                    get() = GraphStatInitException(R.string.graph_stat_view_not_found)
            }
        return createViewData(dataSource, graphOrStat, lineGraph, onDataSampled)
    }

    private suspend fun generatePlottingData(
        dataSource: TrackAndGraphDatabaseDao,
        lineGraph: LineGraphWithFeatures,
        allReferencedDataPoints: MutableList<DataPointInterface>,
        endTime: OffsetDateTime
    ): Map<LineGraphFeature, FastXYSeries?> {
        return lineGraph.features.map { lgf ->
            yield()
            val plottingData =
                tryGetPlottingData(dataSource, lineGraph, allReferencedDataPoints, lgf)
            lgf to plottingData?.let { getXYSeriesFromDataSample(it, endTime, lgf) }
        }.toMap()
    }

    private suspend fun tryGetPlottingData(
        dataSource: TrackAndGraphDatabaseDao,
        lineGraph: LineGraphWithFeatures,
        allReferencedDataPoints: MutableList<DataPointInterface>,
        lineGraphFeature: LineGraphFeature
    ): DataSample? {
        val movingAvDuration = movingAverageDurations[lineGraphFeature.averagingMode]
        val plottingPeriod = plottingModePeriods[lineGraphFeature.plottingMode]
        val rawDataSample = withContext(Dispatchers.IO) {
            val dataSampler = DatabaseSampleHelper(dataSource)
            dataSampler.sampleData(
                lineGraphFeature.featureId,
                lineGraph.duration,
                lineGraph.endDate,
                movingAvDuration,
                plottingPeriod
            )
        }
        val clippingCalculator = DataClippingFunction(lineGraph.endDate, lineGraph.duration)
        val visibleSection = clippingCalculator.execute(rawDataSample)
        allReferencedDataPoints.addAll(visibleSection.dataPoints)

        val timeHelper = TimeHelper(GlobalAggregationPreferences)
        val aggregationCalculator = when (lineGraphFeature.plottingMode) {
            LineGraphPlottingModes.WHEN_TRACKED -> IdentityFunction()
            else -> DurationAggregationFunction(
                timeHelper,
                //We have to add movingAvDuration if it exists to make sure we're going back far enough
                // to get correct averaging
                lineGraph.duration?.plus(movingAvDuration ?: Duration.ZERO),
                lineGraph.endDate,
                plottingPeriod!!
            )
        }
        val averageCalculator = when (lineGraphFeature.averagingMode) {
            LineGraphAveraginModes.NO_AVERAGING -> IdentityFunction()
            else -> MovingAverageFunction(movingAvDuration!!)
        }

        val plottingData = withContext(Dispatchers.Default) {
            CompositeFunction(
                aggregationCalculator,
                averageCalculator,
                clippingCalculator
            ).execute(rawDataSample)
        }

        return if (plottingData.dataPoints.size >= 2) plottingData else null
    }

    private fun getXYSeriesFromDataSample(
        dataSample: DataSample,
        endTime: OffsetDateTime,
        lineGraphFeature: LineGraphFeature
    ): FastXYSeries {
        val scale = lineGraphFeature.scale
        val offset = lineGraphFeature.offset
        val durationDivisor = when (lineGraphFeature.durationPlottingMode) {
            DurationPlottingMode.HOURS -> 3600.0
            DurationPlottingMode.MINUTES -> 60.0
            else -> 1.0
        }
        val yValues = dataSample.dataPoints.map { dp ->
            (dp.value * scale / durationDivisor) + offset
        }
        val xValues =
            dataSample.dataPoints.map { dp -> Duration.between(endTime, dp.timestamp).toMillis() }

        var yRegion = SeriesUtils.minMax(yValues)
        if (abs(yRegion.min.toDouble() - yRegion.max.toDouble()) < 0.1)
            yRegion = Region(yRegion.min, yRegion.min.toDouble() + 0.1)
        val xRegion = SeriesUtils.minMax(xValues)
        val rectRegion = RectRegion(xRegion.min, xRegion.max, yRegion.min, yRegion.max)

        return object : FastXYSeries {
            override fun minMax() = rectRegion
            override fun getX(index: Int): Number = xValues[index]
            override fun getY(index: Int): Number = yValues[index]
            override fun getTitle() = lineGraphFeature.name
            override fun size() = dataSample.dataPoints.size
        }
    }

    private fun getYAxisParameters(
        lineGraph: LineGraphWithFeatures,
        series: Collection<FastXYSeries?>,
        timeBasedRange: Boolean
    ): Pair<RectRegion, Pair<StepMode, Double>> {
        val fixed = lineGraph.yRangeType == YRangeType.FIXED;

        val bounds = RectRegion()
        series.forEach { it?.let { bounds.union(it.minMax()) } }

        val (y_min, y_max) =
            if (fixed) Pair(lineGraph.yFrom, lineGraph.yTo)
            else Pair(bounds.minY, bounds.maxY)

        if (y_min == null || y_max == null) {
            return Pair(bounds, Pair(StepMode.SUBDIVIDE, 11.0))
        }

        val parameters = DataDisplayIntervalHelper()
            .getYParameters(y_min.toDouble(), y_max.toDouble(), timeBasedRange, fixed)

        bounds.minY = parameters.bounds_min
        bounds.maxY = parameters.bounds_max

        val intervalParameters = Pair(parameters.step_mode, parameters.n_intervals)

        return Pair(bounds, intervalParameters)
    }

}