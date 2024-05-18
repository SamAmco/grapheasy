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
package com.samco.trackandgraph.graphstatview.factories

import com.androidplot.util.SeriesUtils
import com.androidplot.xy.RectRegion
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.StepMode
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.BarChart
import com.samco.trackandgraph.base.database.dto.BarChartBarPeriod
import com.samco.trackandgraph.base.database.dto.DataPoint
import com.samco.trackandgraph.base.database.dto.GraphOrStat
import com.samco.trackandgraph.base.database.dto.YRangeType
import com.samco.trackandgraph.base.database.sampling.DataSample
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.functions.aggregation.GlobalAggregationPreferences
import com.samco.trackandgraph.functions.helpers.TimeHelper
import com.samco.trackandgraph.graphstatview.GraphStatInitException
import com.samco.trackandgraph.graphstatview.factories.viewdto.IBarChartViewData
import com.samco.trackandgraph.graphstatview.factories.viewdto.IGraphStatViewData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.threeten.bp.Duration
import org.threeten.bp.Period
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.temporal.TemporalAmount
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

class BarChartDataFactory @Inject constructor(
    dataInteractor: DataInteractor,
    @IODispatcher ioDispatcher: CoroutineDispatcher
) : ViewDataFactory<BarChart, IBarChartViewData>(dataInteractor, ioDispatcher) {

    companion object {

        @VisibleForTesting
        data class BarData(
            val bars: List<SimpleXYSeries>,
            val dates: List<ZonedDateTime>,
            val bounds: RectRegion
        )

        private fun BarChartBarPeriod.asTemporalAmount(): TemporalAmount = when (this) {
            BarChartBarPeriod.HOUR -> Duration.ofHours(1)
            BarChartBarPeriod.DAY -> Period.ofDays(1)
            BarChartBarPeriod.WEEK -> Period.ofWeeks(1)
            BarChartBarPeriod.MONTH -> Period.ofMonths(1)
            BarChartBarPeriod.THREE_MONTHS -> Period.ofMonths(3)
            BarChartBarPeriod.SIX_MONTHS -> Period.ofMonths(6)
            BarChartBarPeriod.YEAR -> Period.ofYears(1)
        }

        /**
         * Calculates the bar data for a bar chart. The output is:
         *
         * - A list of SimpleXYSeries. Each SimpleXYSeries represents all the bars for a given label.
         *  the series are sorted by the sum of their values, in descending order.
         *
         * - A list of ZonedDateTimes. Each ZonedDateTime represents the end of a bar. The dates
         *  should ascend in order spaced by barSize. The list of bars and dates should always be the
         *  same size.
         *
         * - A RectRegion representing the bounds of the data. The x bounds will be 0 to the number of
         * bars. The y bounds will be 0 to the max stacked value of any bar or [yTo] if [yRangeType] is [YRangeType.FIXED].
         *
         * There should be one bar per [barSize] between [endTime] and [endTime] - [sampleSize].
         *
         * All values in every SimpleXYSeries will be multiplied by [scale]. If you use [sumByCount],
         * the value multiplied will be the number of data points for a bar rather than the sum of
         * their values.
         */
        @VisibleForTesting
        fun getBarData(
            timeHelper: TimeHelper,
            dataSample: DataSample,
            endTime: ZonedDateTime?,
            barSize: BarChartBarPeriod,
            sampleSize: TemporalAmount?,
            sumByCount: Boolean,
            yRangeType: YRangeType,
            yTo: Double,
            scale: Double
        ): BarData {
            val barDates = mutableListOf<ZonedDateTime>()
            val barValuesByLabel = mutableMapOf<String, MutableList<Double>>()
            val iterator = dataSample.iterator()
            val barPeriod = barSize.asTemporalAmount()

            //Some variables we will calculate as soon as we have the first data point
            // and use to iterate backwards from the end time grouping the data points into bars
            var roundedEndTime: ZonedDateTime? = null
            var endTimeMinusDuration: ZonedDateTime? = null
            var currentBarStartTime: ZonedDateTime? = null
            var currentBarEndTime: ZonedDateTime? = null

            //Then count backwards from the last end time to the start time of the duration
            while (iterator.hasNext()) {
                //Grab the next data point to be placed
                val next = iterator.next()
                val timestamp = timeHelper.toZonedDateTime(next.timestamp)

                //If we were passed a null end time find the end time of the first data point
                // instead
                if (roundedEndTime == null) {
                    roundedEndTime = endTime?.let {
                        timeHelper.findEndOfTemporal(it, barSize.asTemporalAmount())
                    } ?: timeHelper.findEndOfTemporal(timestamp, barSize.asTemporalAmount())
                    currentBarStartTime = roundedEndTime.minus(barPeriod)
                    currentBarEndTime = roundedEndTime
                    endTimeMinusDuration = sampleSize?.let { roundedEndTime.minus(it) }

                    barDates.add(roundedEndTime)
                }

                //If the next data point is before the start of the duration we are interested in, we
                // can stop
                if (endTimeMinusDuration != null && timestamp.isBefore(endTimeMinusDuration)) break

                while (timestamp.isBefore(currentBarStartTime)) {
                    //we have reached the end of the current bar, so we need to move to the next one
                    currentBarStartTime = currentBarStartTime!!.minus(barPeriod)
                    currentBarEndTime = currentBarEndTime!!.minus(barPeriod)
                    barDates.add(currentBarEndTime)
                }

                //Get the list of values for the label of the next data point. If there isn't one, create
                // a new one and add it to barValuesByLabel
                val values = barValuesByLabel.getOrPut(next.label) { mutableListOf() }

                //If the list is not the same size as barDates, we need to add some zeros to the end
                // of all the lists until they're all the same size as barDates
                if (values.size < barDates.size) barValuesByLabel.values.forEach {
                    it.addAll(List(barDates.size - it.size) { 0.0 })
                }

                //Add the value to the double in the list
                values[values.size - 1] += if (sumByCount) 1.0 else next.value
            }

            //Multiply all values by scale
            barValuesByLabel.forEach { (_, u) ->
                u.forEachIndexed { index, value ->
                    u[index] = value * scale
                }
            }

            val barSumsByLabel = barValuesByLabel
                .mapValues { (_, values) -> values.sum() }
                .toList()
                .sortedByDescending { (_, value) -> value }

            val bars = barValuesByLabel
                .map { (label, values) ->
                    //Reverse the order because the values are added from newest to
                    // oldest but should be displayed from oldest to newest
                    SimpleXYSeries(
                        values.asReversed(),
                        SimpleXYSeries.ArrayFormat.Y_VALS_ONLY,
                        label
                    )
                }
                //Sort the layers from largest to smallest so the label with the largest total of
                // values is on the bottom
                .sortedBy { label -> barSumsByLabel.indexOfFirst { it.first == label.title } }

            // reverse the order because the values are added from newest to
            // oldest but should be displayed from oldest to newest
            val dates = barDates.asReversed()

            //The values are essentially a grid and we want the largest column sum
            val maxY = (0 until (barValuesByLabel.values.firstOrNull()?.size ?: 0))
                .maxOfOrNull { index -> barValuesByLabel.values.sumOf { it[index] } } ?: 0.0

            //If maxY is 0, we want to show a range of 0 to 1
            val maxYForRange = if (abs(maxY) < 0.0000001) 1.0 else maxY

            val xRegion = SeriesUtils.minMax(listOf(-0.5, (barDates.size - 1) + 0.5))
            val yRegion = SeriesUtils.minMax(
                if (yRangeType == YRangeType.FIXED) listOf(0.0, yTo)
                else listOf(0.0, maxYForRange)
            )
            val bounds = RectRegion(xRegion.min, xRegion.max, yRegion.min, yRegion.max)

            return BarData(bars, dates, bounds)
        }
    }

    override suspend fun createViewData(
        graphOrStat: GraphOrStat,
        onDataSampled: (List<DataPoint>) -> Unit
    ): IBarChartViewData = dataInteractor.getBarChartByGraphStatId(graphOrStat.id)?.let {
        createViewData(graphOrStat, it, onDataSampled)
    } ?: object : IBarChartViewData {
        override val state = IGraphStatViewData.State.ERROR
        override val error = GraphStatInitException(R.string.graph_stat_view_not_found)
        override val graphOrStat = graphOrStat
    }

    override suspend fun affectedBy(graphOrStatId: Long, featureId: Long): Boolean {
        return dataInteractor.getBarChartByGraphStatId(graphOrStatId)?.featureId == featureId
    }

    private data class BarDataWithYAxisParams(
        val bars: List<SimpleXYSeries>,
        val dates: List<ZonedDateTime>,
        val bounds: RectRegion,
        val yAxisParameters: Pair<StepMode, Double>,
    )

    private fun getBarDataWithYAxisParams(
        timeHelper: TimeHelper,
        dataSample: DataSample,
        endTime: ZonedDateTime?,
        config: BarChart
    ): BarDataWithYAxisParams {

        val barData = getBarData(
            timeHelper = timeHelper,
            dataSample = dataSample,
            endTime = endTime,
            barSize = config.barPeriod,
            sampleSize = config.sampleSize,
            sumByCount = config.sumByCount,
            yRangeType = config.yRangeType,
            yTo = config.yTo,
            scale = config.scale
        )

        val yAxisParameters = DataDisplayIntervalHelper().getYParameters(
            barData.bounds.minY.toDouble(),
            barData.bounds.maxY.toDouble(),
            dataSample.dataSampleProperties.isDuration,
            config.yRangeType == YRangeType.FIXED
        )

        val yAxisParameterPair = Pair(yAxisParameters.step_mode, yAxisParameters.n_intervals)

        return BarDataWithYAxisParams(
            bars = barData.bars,
            dates = barData.dates,
            bounds = barData.bounds,
            yAxisParameters = yAxisParameterPair
        )
    }

    override suspend fun createViewData(
        graphOrStat: GraphOrStat,
        config: BarChart,
        onDataSampled: (List<DataPoint>) -> Unit
    ): IBarChartViewData {
        val dataSample = dataInteractor.getDataSampleForFeatureId(config.featureId)

        if (!dataSample.iterator().hasNext()) return object : IBarChartViewData {
            override val state = IGraphStatViewData.State.READY
            override val graphOrStat = graphOrStat
        }

        try {
            //TODO basically everywhere you see zone id i think you might wanna use time helper
            // and inject it.
            val timeHelper = TimeHelper(GlobalAggregationPreferences)
            val endTime = config.endDate.toOffsetDateTime()?.let { timeHelper.toZonedDateTime(it) }

            val barData = withContext(ioDispatcher) {
                getBarDataWithYAxisParams(
                    timeHelper = timeHelper,
                    dataSample = dataSample,
                    endTime = endTime,
                    config = config,
                )
            }

            onDataSampled(dataSample.getRawDataPoints())

            return object : IBarChartViewData {
                override val xDates = barData.dates
                override val bars = barData.bars
                override val durationBasedRange = dataSample.dataSampleProperties.isDuration
                override val endTime = endTime ?: barData.dates.last()
                override val bounds = barData.bounds
                override val yAxisRangeParameters = barData.yAxisParameters
                override val state = IGraphStatViewData.State.READY
                override val graphOrStat = graphOrStat
                override val barPeriod = config.barPeriod.asTemporalAmount()
            }
        } catch (t: Throwable) {
            Timber.d(t, "Error creating bar chart data")
            return object : IBarChartViewData {
                override val state = IGraphStatViewData.State.ERROR
                override val error = GraphStatInitException(R.string.graph_stat_validation_unknown)
                override val graphOrStat = graphOrStat
            }
        } finally {
            dataSample.dispose()
        }
    }
}