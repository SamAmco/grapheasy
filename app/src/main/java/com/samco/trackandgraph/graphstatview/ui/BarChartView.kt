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
package com.samco.trackandgraph.graphstatview.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import androidx.compose.material.Surface
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.core.content.ContextCompat.getColor
import com.androidplot.ui.VerticalPosition
import com.androidplot.ui.VerticalPositioning
import com.androidplot.util.PixelUtils
import com.androidplot.xy.BarFormatter
import com.androidplot.xy.BarRenderer
import com.androidplot.xy.BoundaryMode
import com.androidplot.xy.PanZoom
import com.androidplot.xy.RectRegion
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.StepMode
import com.androidplot.xy.XValueMarker
import com.androidplot.xy.XYGraphWidget
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.helpers.formatDayMonthYearHourMinute
import com.samco.trackandgraph.base.helpers.formatTimeDuration
import com.samco.trackandgraph.base.helpers.getDayMonthFormatter
import com.samco.trackandgraph.base.helpers.getMonthYearFormatter
import com.samco.trackandgraph.databinding.GraphXyPlotBinding
import com.samco.trackandgraph.graphstatview.factories.viewdto.IBarChartViewData
import com.samco.trackandgraph.ui.compose.ui.ColorCircle
import com.samco.trackandgraph.ui.compose.ui.SpacingExtraSmall
import com.samco.trackandgraph.ui.compose.ui.SpacingSmall
import com.samco.trackandgraph.ui.dataVisColorGenerator
import com.samco.trackandgraph.ui.dataVisColorList
import com.samco.trackandgraph.util.getColorFromAttr
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.TemporalAmount
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class BarMarkerStore {
    var highlightedIndex by mutableStateOf(null as Int?)
        private set

    private val lock = Any()

    fun setHighlightedIndex(index: Int) = synchronized(lock) {
        highlightedIndex = index
    }

    fun clearHighlightedIndex() = synchronized(lock) {
        highlightedIndex = null
    }
}

@Composable
fun BarChartView(
    modifier: Modifier = Modifier,
    viewData: IBarChartViewData,
    listMode: Boolean,
    timeMarker: OffsetDateTime? = null,
    graphHeight: Int? = null
) = Box(modifier = modifier) {
    if (viewData.xDates.isEmpty() || viewData.bars.isEmpty()) {
        GraphErrorView(
            error = R.string.graph_stat_view_not_enough_data_graph
        )
    } else {
        val barMarkerStore = remember(timeMarker) {
            val barMarkerStore = BarMarkerStore()
            timeMarker?.let { marker ->
                val zonedMarker = marker.atZoneSameInstant(viewData.endTime.zone)
                val index = viewData.xDates.indexOfLast { zonedMarker.isAfter(it) } + 1
                if (index in viewData.xDates.indices) barMarkerStore.setHighlightedIndex(index)
            }
            barMarkerStore
        }

        BarChartBodyView(
            xDates = viewData.xDates,
            bars = viewData.bars,
            durationBasedRange = viewData.durationBasedRange,
            endTime = viewData.endTime,
            bounds = viewData.bounds,
            yAxisRangeParameters = viewData.yAxisRangeParameters,
            listMode = listMode,
            barMarkerStore = barMarkerStore,
            graphHeight = graphHeight
        )

        if (!listMode) barMarkerStore.highlightedIndex?.let {
            BarChartDataOverlay(
                modifier = Modifier
                    .wrapContentHeight(Alignment.Top)
                    .align(Alignment.TopEnd),
                context = LocalContext.current,
                highlightedIndex = it,
                xDates = viewData.xDates,
                bars = viewData.bars,
                barPeriod = viewData.barPeriod,
                viewData.durationBasedRange
            )
        }
    }
}

private fun doubleToString(value: Double, maxPlaces: Int = 3): String {
    val scale = min(maxPlaces, value.toBigDecimal().scale())
    return String.format("%.${scale}f", value)
}

@Composable
private fun BarChartDataOverlay(
    modifier: Modifier = Modifier,
    context: Context,
    highlightedIndex: Int,
    xDates: List<ZonedDateTime>,
    bars: List<SimpleXYSeries>,
    barPeriod: TemporalAmount,
    durationBasedRange: Boolean
) = Surface(
    modifier = modifier
        .width(IntrinsicSize.Max)
        .animateContentSize()
) {

    val total = remember(highlightedIndex, bars) {
        val totalVal = bars.sumOf { it.getyVals()[highlightedIndex].toDouble() }
        if (durationBasedRange) formatTimeDuration(totalVal.toLong())
        else doubleToString(totalVal)
    }

    val fromText = remember(highlightedIndex, xDates) {
        val from = xDates[highlightedIndex].minus(barPeriod)
        formatDayMonthYearHourMinute(context, from)
    }

    val toText = remember(highlightedIndex, xDates) {
        formatDayMonthYearHourMinute(context, xDates[highlightedIndex])
    }

    //a list of label: value (percentage) strings for each label for the current bar
    val extraDetails = remember(highlightedIndex, xDates, bars) {
        val values = bars.associate {
            it.title to it.getyVals()[highlightedIndex].toDouble()
        }
        val sum = values.values.sum()

        if (sum < 1e-6) emptyList()
        else bars.map {
            val value = values[it.title] ?: 0.0
            val percentage = (value / sum) * 100.0
            val percentageStr = doubleToString(percentage, 1)
            val str =
                if (durationBasedRange) formatTimeDuration(value.toLong())
                else doubleToString(value)
            return@map "${it.title}: $str ($percentageStr%)"
        }
    }

    Column(
        modifier = Modifier.padding(dimensionResource(id = R.dimen.card_padding))
    ) {
        Text(
            text = stringResource(id = R.string.from_formatted, fromText),
            style = MaterialTheme.typography.body1,
        )

        Text(
            text = stringResource(id = R.string.to_formatted, toText),
            style = MaterialTheme.typography.body1,
        )

        Text(
            text = stringResource(id = R.string.total_formatted, total),
            style = MaterialTheme.typography.body1,
        )

        if (extraDetails.isNotEmpty()) BarChartDataOverlayExtraDetails(extraDetails)
    }
}

@Composable
private fun BarChartDataOverlayExtraDetails(
    extraDetails: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    SpacingSmall()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.info),
            style = MaterialTheme.typography.body1,
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .rotate(if (expanded) 180f else 0f)
        )
    }


    SpacingSmall()

    extraDetails.forEachIndexed { index, labelInfo ->
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row {
                val colorIndex = (index * dataVisColorGenerator) % dataVisColorList.size
                ColorCircle(
                    color = dataVisColorList[colorIndex],
                    size = 16.dp
                )
                SpacingExtraSmall()
                Text(
                    text = labelInfo,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BarChartBodyView(
    modifier: Modifier = Modifier,
    xDates: List<ZonedDateTime>,
    bars: List<SimpleXYSeries>,
    durationBasedRange: Boolean,
    endTime: ZonedDateTime,
    bounds: RectRegion,
    yAxisRangeParameters: Pair<StepMode, Double>,
    listMode: Boolean,
    barMarkerStore: BarMarkerStore,
    graphHeight: Int? = null
) = Column(modifier = modifier) {

    val context = LocalContext.current

    AndroidViewBinding(
        factory = { inflater, parent, attachToParent ->
            val binding = GraphXyPlotBinding.inflate(inflater, parent, attachToParent)

            xyPlotSetup(
                context = context,
                xyPlot = binding.xyPlot
            )
            binding.xyPlot.clear()

            setBarChartBounds(
                binding = binding,
                bounds = bounds,
            )

            val xAxisFormatter = getXAxisFormatter(
                context = context,
                xDates = xDates,
            )

            setXAxisFormatter(
                binding = binding,
                xDates = xDates,
                xAxisFormatter = xAxisFormatter
            )

            setBarChartPaddingFromBounds(
                context = context,
                binding = binding,
                bounds = bounds,
                endTime = endTime,
                xAxisFormatter = xAxisFormatter
            )

            setUpXYPlotYAxis(
                binding = binding,
                yAxisRangeParameters = yAxisRangeParameters,
                durationBasedRange = durationBasedRange
            )
            drawBars(
                context = context,
                binding = binding,
                bars = bars
            )


            return@AndroidViewBinding binding
        },
        update = {

            setXAxisNumLabels(
                context = context,
                binding = this,
                xDates = xDates,
            )

            if (!listMode) {
                attachPanZoomClickListener(
                    binding = this,
                    barMarkerStore = barMarkerStore,
                    barIndexRange = xDates.indices
                )
                redrawMarkers(
                    binding = this,
                    barMarkerStore = barMarkerStore,
                )
            }

            if (graphHeight != null) xyPlot.layoutParams.height = graphHeight
            xyPlot.requestLayout()
            xyPlot.redraw()
        })

    SpacingSmall()

    if (bars.size > 1) {
        GraphLegend(
            items = bars.mapIndexed { i, bar ->
                val colorIndex = (i * dataVisColorGenerator) % dataVisColorList.size
                val label = bar.title
                    .ifEmpty { context.getString(R.string.no_label) }
                GraphLegendItem(
                    color = dataVisColorList[colorIndex],
                    label = label
                )
            }
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
private fun attachPanZoomClickListener(
    binding: GraphXyPlotBinding,
    barMarkerStore: BarMarkerStore,
    barIndexRange: IntRange
) {
    val renderer = binding.xyPlot.getRenderer(BarRenderer::class.java)

    binding.xyPlot.setOnTouchListener(object : PanZoom(
        binding.xyPlot,
        Pan.HORIZONTAL,
        Zoom.STRETCH_HORIZONTAL
    ) {

        private var down = false
        private var pointerX = 0f
        private var pointerY = 0f

        override fun zoom(motionEvent: MotionEvent?) {
            super.zoom(motionEvent)
            redrawMarkers(binding, barMarkerStore)
        }

        override fun pan(motionEvent: MotionEvent?) {
            super.pan(motionEvent)
            redrawMarkers(binding, barMarkerStore)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            //Detect a click from a single pointer, otherwise call super
            if (event.pointerCount == 1) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        down = true
                        pointerX = event.x
                        pointerY = event.y
                        return super.onTouch(view, event)
                    }

                    MotionEvent.ACTION_UP -> {
                        val smallX = abs(event.x - pointerX) < 10
                        val smallY = abs(event.y - pointerY) < 10
                        if (down && smallX && smallY) onBarClick(event, view)
                        down = false
                    }

                    else -> {
                        down = false
                        return super.onTouch(view, event)
                    }
                }
            } else {
                down = false
                return super.onTouch(view, event)
            }
            return super.onTouch(view, event)
        }

        private fun onBarClick(event: MotionEvent, view: View) {
            val rounded = renderer.plot.screenToSeriesX(event.x).toDouble().roundToInt()
            if (rounded in barIndexRange) {
                toggleBarMarker(
                    binding = binding,
                    barMarkerStore = barMarkerStore,
                    index = rounded
                )
                view.performClick()
            }
        }
    })
}

fun setXAxisFormatter(
    binding: GraphXyPlotBinding,
    xDates: List<ZonedDateTime>,
    xAxisFormatter: DateTimeFormatter
) {
    binding.xyPlot.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).format =
        object : Format() {
            override fun format(
                obj: Any,
                toAppendTo: StringBuffer,
                pos: FieldPosition
            ): StringBuffer {
                //We minus a tiny amount to favour labelling the previous bar if we're right on the
                // border between two
                val number = (obj as Number).toDouble() - 0.0001
                val rounded = number.roundToInt()
                //Shouldn't ever happen that we get a date outside of the range, but just in case
                val date =
                    if (rounded in xDates.indices) xDates[rounded]
                    else if (rounded < 0) xDates.first()
                    else xDates.last()

                return toAppendTo.append(
                    if (number < 0) ""
                    else date.format(xAxisFormatter)
                )
            }

            override fun parseObject(source: String, pos: ParsePosition) = null
        }
}

private fun setXAxisNumLabels(
    context: Context,
    binding: GraphXyPlotBinding,
    xDates: List<ZonedDateTime>,
) {
    val displayMetrics = context.resources.displayMetrics
    val dpWidth = binding.xyPlot.width / displayMetrics.density
    if (dpWidth < 0.1f) return

    val maxLabels = (dpWidth / 30.0).toInt().toDouble()

    var xStep = xDates.size
    for (i in 1 until (xDates.size / 2)) {
        if (xDates.size / i <= maxLabels && xDates.size % i == 0) {
            xStep = xDates.size / i
            break
        }
    }

    val subdivisions = min((xStep + 1).toDouble(), maxLabels)

    binding.xyPlot.setDomainStep(StepMode.SUBDIVIDE, subdivisions)
}

private fun setBarChartBounds(binding: GraphXyPlotBinding, bounds: RectRegion) {
    binding.xyPlot.setRangeBoundaries(bounds.minY, bounds.maxY, BoundaryMode.FIXED)
    binding.xyPlot.bounds.set(bounds.minX, bounds.maxX, bounds.minY, bounds.maxY)
    binding.xyPlot.outerLimits.set(bounds.minX, bounds.maxX, bounds.minY, bounds.maxY)
}

private fun setBarChartPaddingFromBounds(
    context: Context,
    binding: GraphXyPlotBinding,
    bounds: RectRegion,
    endTime: ZonedDateTime,
    xAxisFormatter: DateTimeFormatter
) {
    //Set up Y padding
    val minY = bounds.minY.toDouble()
    val maxY = bounds.maxY.toDouble()
    val maxBound = max(abs(minY), abs(maxY))
    val numDigits = log10(maxBound).toFloat() + 3
    binding.xyPlot.graph.paddingLeft =
        (numDigits - 1) * (context.resources.displayMetrics.scaledDensity) * 3.5f

    //Set up X padding
    val formattedTimestamp = xAxisFormatter.format(endTime)
    binding.xyPlot.graph.paddingBottom =
        formattedTimestamp.length * (context.resources.displayMetrics.scaledDensity)
}

private fun getXAxisFormatter(
    context: Context,
    xDates: List<ZonedDateTime>
): DateTimeFormatter {
    val minX = xDates.firstOrNull()
    val maxX = xDates.lastOrNull()
    if (minX == null || maxX == null) return getDayMonthFormatter(context)
    val durationRange = Duration.between(minX, maxX)
    return when {
        durationRange.toMinutes() < 5L -> DateTimeFormatter.ofPattern("HH:mm:ss")
        durationRange.toDays() >= 304 -> getMonthYearFormatter(context)
        durationRange.toDays() >= 1 -> getDayMonthFormatter(context)
        else -> DateTimeFormatter.ofPattern("HH:mm")
    }
}

private fun drawBars(
    context: Context,
    binding: GraphXyPlotBinding,
    bars: List<SimpleXYSeries>
) {
    val outlineColor = context.getColorFromAttr(R.attr.colorOnSurface)

    // if there are more than 60 bars, we don't want to draw the borders
    // I chose 60 simply because it's the first round number after the number of weeks in a year
    val xfermode =
        if (bars.isNotEmpty() && bars[0].getyVals().size < 60) null
        else PorterDuffXfermode(PorterDuff.Mode.DST)

    bars.forEachIndexed { i, bv ->
        val colorIndex = (i * dataVisColorGenerator) % dataVisColorList.size
        val color = getColor(context, dataVisColorList[colorIndex])
        val seriesFormatter = BarFormatter(color, outlineColor)
        seriesFormatter.borderPaint.xfermode = xfermode
        binding.xyPlot.addSeries(bv, seriesFormatter)
    }

    val renderer = binding.xyPlot.getRenderer(BarRenderer::class.java)
    renderer.setBarGroupWidth(BarRenderer.BarGroupWidthMode.FIXED_GAP, PixelUtils.dpToPix(0f))
    renderer.barOrientation = BarRenderer.BarOrientation.STACKED
}

private fun getMarkerPaint(binding: GraphXyPlotBinding): Paint {
    //Calculate the marker width
    val renderer = binding.xyPlot.getRenderer(BarRenderer::class.java)
    //If the gridRect is null it will throw an exception, this can happen if the graph hasn't been
    // laid out yet
    if (binding.xyPlot.graph.gridRect == null) return Paint()
    val width = abs(renderer.plot.seriesToScreenX(1) - renderer.plot.seriesToScreenX(0))

    val paint = Paint()
    paint.color = binding.root.context.getColorFromAttr(R.attr.colorOnSurface)
    paint.alpha = (0.2f * 255f).toInt()
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    paint.strokeWidth = width
    return paint
}

private fun toggleBarMarker(
    binding: GraphXyPlotBinding,
    barMarkerStore: BarMarkerStore,
    index: Int
) {
    if (barMarkerStore.highlightedIndex == index) barMarkerStore.clearHighlightedIndex()
    else barMarkerStore.setHighlightedIndex(index)
    redrawMarkers(binding, barMarkerStore)
}

private fun redrawMarkers(
    binding: GraphXyPlotBinding,
    barMarkerStore: BarMarkerStore
) {
    binding.xyPlot.removeMarkers()
    val markerPaint = getMarkerPaint(binding)
    barMarkerStore.highlightedIndex?.let {
        binding.xyPlot.addMarker(
            XValueMarker(
                it,
                null,
                VerticalPosition(0f, VerticalPositioning.ABSOLUTE_FROM_TOP),
                markerPaint,
                null
            )
        )
    }
    binding.xyPlot.redraw()
}
