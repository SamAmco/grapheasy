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
import com.samco.trackandgraph.base.database.dto.*
import com.samco.trackandgraph.base.database.sampling.DataSample
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.DefaultDispatcher
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.functions.aggregation.GlobalAggregationPreferences
import com.samco.trackandgraph.functions.functions.*
import com.samco.trackandgraph.functions.helpers.TimeHelper
import com.samco.trackandgraph.graphstatview.GraphStatInitException
import com.samco.trackandgraph.graphstatview.factories.viewdto.IGraphStatViewData
import com.samco.trackandgraph.graphstatview.factories.viewdto.ILineGraphViewData
import com.samco.trackandgraph.movingAverageDurations
import com.samco.trackandgraph.plottingModePeriods
import kotlinx.coroutines.*
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import javax.inject.Inject
import kotlin.math.abs

class LineGraphDataFactory @Inject constructor(
    dataInteractor: DataInteractor,
    @IODispatcher ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewDataFactory<LineGraphWithFeatures, ILineGraphViewData>(dataInteractor, ioDispatcher) {

    override suspend fun createViewData(
        graphOrStat: GraphOrStat,
        config: LineGraphWithFeatures,
        onDataSampled: (List<DataPoint>) -> Unit
    ): ILineGraphViewData {
        try {
            val plottableData = generatePlottingData(config, onDataSampled)
            val hasPlottableData = plottableData
                .seriesPerFeature
                .any { kvp -> kvp.value != null }

            val durationBasedRange = config.features
                .any { f -> f.durationPlottingMode == DurationPlottingMode.DURATION_IF_POSSIBLE }
            val (bounds, yAxisParameters) = getYAxisParameters(
                config,
                plottableData.seriesPerFeature.values,
                durationBasedRange
            )

            return object : ILineGraphViewData {
                override val durationBasedRange = durationBasedRange
                override val yRangeType = config.yRangeType
                override val bounds = bounds
                override val hasPlottableData = hasPlottableData
                override val endTime = plottableData.endTime
                override val plottableData = plottableData.seriesPerFeature
                override val state = IGraphStatViewData.State.READY
                override val graphOrStat = graphOrStat
                override val yAxisRangeParameters = yAxisParameters
            }
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            return object : ILineGraphViewData {
                override val state = IGraphStatViewData.State.ERROR
                override val graphOrStat = graphOrStat
                override val error = throwable
            }
        }
    }

    override suspend fun createViewData(
        graphOrStat: GraphOrStat,
        onDataSampled: (List<DataPoint>) -> Unit
    ): ILineGraphViewData {
        val lineGraph = dataInteractor.getLineGraphByGraphStatId(graphOrStat.id)
            ?: return object : ILineGraphViewData {
                override val state = IGraphStatViewData.State.ERROR
                override val graphOrStat = graphOrStat
                override val error = GraphStatInitException(R.string.graph_stat_view_not_found)
            }
        return createViewData(graphOrStat, lineGraph, onDataSampled)
    }

    override suspend fun affectedBy(graphOrStatId: Long, featureId: Long): Boolean {
        return dataInteractor.getLineGraphByGraphStatId(graphOrStatId)?.features
            ?.any { it.featureId == featureId } ?: false
    }

    private data class PlottingData(
        val seriesPerFeature: Map<LineGraphFeature, FastXYSeries?>,
        val endTime: OffsetDateTime
    )

    private suspend fun generatePlottingData(
        lineGraph: LineGraphWithFeatures,
        onDataSampled: (List<DataPoint>) -> Unit
    ): PlottingData = coroutineScope {
        withContext(defaultDispatcher) {
            //Create all the data samples in parallel (this shouldn't actually take long but why not)
            val dataSamples = lineGraph.features.map { lgf ->
                async { Pair(lgf, tryGetPlottingData(lineGraph, lgf)) }
            }.awaitAll()

            //Get the end time of the graph. If not specified it's the time of the last data
            // point of any of the features
            val endTime = lineGraph.endDate.toOffsetDateTime() ?: dataSamples
                .mapNotNull { it.second.firstOrNull() }
                .maxOfOrNull { it.timestamp }
                ?: OffsetDateTime.now()

            //Generate the actual plotting data for each sample. This is the part that will take longer
            // hence the parallelization
            val features = dataSamples.map { pair ->
                async {
                    val clippedSample = DataClippingFunction(endTime, lineGraph.sampleSize)
                        .mapSample(pair.second)

                    //Calling toList on the data sample evaluates it and causes the whole pipeline
                    // to be processed
                    val dataPoints = clippedSample.toList().asReversed()

                    val series = if (dataPoints.size >= 2) {
                        getXYSeriesFromDataPoints(dataPoints, endTime, pair.first)
                    } else null
                    pair.first to series
                }
            }.awaitAll().toMap()

            val rawDataPoints = dataSamples
                .map { it.second.getRawDataPoints() }
                .flatten()
            onDataSampled(rawDataPoints)
            dataSamples.forEach { it.second.dispose() }

            return@withContext PlottingData(
                features,
                endTime
            )
        }
    }

    private suspend fun tryGetPlottingData(
        config: LineGraphWithFeatures,
        lineGraphFeature: LineGraphFeature
    ): DataSample {
        val movingAvDuration = movingAverageDurations[lineGraphFeature.averagingMode]
        val plottingPeriod = plottingModePeriods[lineGraphFeature.plottingMode]
        val rawDataSample = withContext(ioDispatcher) {
            dataInteractor.getDataSampleForFeatureId(lineGraphFeature.featureId)
        }

        val timeHelper = TimeHelper(GlobalAggregationPreferences)
        val aggregationCalculator = when (lineGraphFeature.plottingMode) {
            LineGraphPlottingModes.WHEN_TRACKED -> IdentityFunction()
            else -> CompositeFunction(
                DurationAggregationFunction(timeHelper, plottingPeriod!!),
                DataPaddingFunction(timeHelper, config.endDate.toOffsetDateTime(), config.sampleSize)
            )
        }
        val averageCalculator = when (lineGraphFeature.averagingMode) {
            LineGraphAveraginModes.NO_AVERAGING -> IdentityFunction()
            else -> MovingAverageFunction(movingAvDuration!!)
        }
        return CompositeFunction(aggregationCalculator, averageCalculator)
            .mapSample(rawDataSample)
    }

    private fun getXYSeriesFromDataPoints(
        dataSample: List<IDataPoint>,
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

        val yValues = dataSample.map { dp ->
            (dp.value * scale / durationDivisor) + offset
        }
        val xValues = dataSample.map { dp ->
            Duration.between(endTime, dp.timestamp).toMillis()
        }

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
            override fun size() = xValues.size
        }
    }

    private fun getYAxisParameters(
        lineGraph: LineGraphWithFeatures,
        series: Collection<FastXYSeries?>,
        timeBasedRange: Boolean
    ): Pair<RectRegion, Pair<StepMode, Double>> {
        val fixed = lineGraph.yRangeType == YRangeType.FIXED

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