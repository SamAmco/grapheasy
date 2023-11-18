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

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.core.content.ContextCompat.getColor
import com.androidplot.ui.VerticalPosition
import com.androidplot.ui.VerticalPositioning
import com.androidplot.xy.*
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.LineGraphFeature
import com.samco.trackandgraph.base.database.dto.LineGraphPointStyle
import com.samco.trackandgraph.base.database.dto.YRangeType
import com.samco.trackandgraph.base.helpers.formatDayMonth
import com.samco.trackandgraph.base.helpers.formatMonthYear
import com.samco.trackandgraph.databinding.GraphXyPlotBinding
import com.samco.trackandgraph.graphstatview.factories.viewdto.ILineGraphViewData
import com.samco.trackandgraph.ui.dataVisColorList
import com.samco.trackandgraph.util.getColorFromAttr
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.Temporal
import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

@Composable
fun LineGraphView(
    modifier: Modifier = Modifier,
    viewData: ILineGraphViewData,
    listMode: Boolean,
    timeMarker: OffsetDateTime? = null,
    graphHeight: Int? = null
) {
    if (!viewData.hasPlottableData) {
        GraphErrorView(
            modifier = modifier,
            error = R.string.graph_stat_view_not_enough_data_graph
        )
    } else {
        LineGraphBodyView(
            modifier = modifier,
            viewData = viewData,
            timeMarker = timeMarker,
            listMode = listMode,
            graphHeight = graphHeight
        )
    }
}

@Composable
fun LineGraphBodyView(
    modifier: Modifier,
    viewData: ILineGraphViewData,
    timeMarker: OffsetDateTime? = null,
    listMode: Boolean,
    graphHeight: Int? = null
) = Column(modifier = modifier) {

    val context = LocalContext.current

    val zonedEndTime = viewData.endTime.atZoneSameInstant(ZoneId.systemDefault())

    AndroidViewBinding(factory = { inflater, parent, attachToParent ->
        val binding = GraphXyPlotBinding.inflate(inflater, parent, attachToParent)

        xyPlotSetup(
            context = context,
            xyPlot = binding.xyPlot
        )
        binding.xyPlot.clear()

        drawLineGraphFeatures(
            context = context,
            binding = binding,
            plottableData = viewData.plottableData,
            listMode = listMode,
        )
        setXAxisFormatting(
            context = context,
            binding = binding,
            zonedEndTime = zonedEndTime
        )
        setUpXYPlotYAxis(
            binding = binding,
            yAxisRangeParameters = viewData.yAxisRangeParameters,
            durationBasedRange = viewData.durationBasedRange,
        )
        setLineGraphBounds(
            context = context,
            binding = binding,
            bounds = viewData.bounds,
            yRangeType = viewData.yRangeType,
            zonedEndTime = zonedEndTime,
            listMode = listMode
        )

        if (!listMode) {
            PanZoom.attach(binding.xyPlot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.STRETCH_HORIZONTAL)
        }

        return@AndroidViewBinding binding
    }, update = {
        setXNumLabels(
            context = context,
            binding = this,
            bounds = viewData.bounds
        )
        setTimeMarker(
            context = context,
            binding = this,
            endTime = zonedEndTime,
            timeMarker = timeMarker
        )

        if (graphHeight != null) xyPlot.layoutParams.height = graphHeight
        xyPlot.requestLayout()
    })

    GraphLegend(
        items = viewData.plottableData.map {
            GraphLegendItem(
                color = dataVisColorList[it.key.colorIndex],
                label = it.key.name
            )
        }
    )
}

private val lineGraphHourMinuteSecondFormat: DateTimeFormatter = DateTimeFormatter
    .ofPattern("HH:mm:ss")
private val lineGraphHoursDateFormat: DateTimeFormatter = DateTimeFormatter
    .ofPattern("HH:mm")

private fun setLineGraphBounds(
    context: Context,
    binding: GraphXyPlotBinding,
    bounds: RectRegion,
    yRangeType: YRangeType,
    zonedEndTime: ZonedDateTime,
    listMode: Boolean
) {
    // since we now calculate the bounds to fit the number of intervals we almost always want
    // to set the rangeBoundaries to the bounds.
    // The only exception is when the graph is viewed fullscreen-mode (listMode == False) while dynamic
    if (yRangeType == YRangeType.FIXED || listMode) {
        binding.xyPlot.setRangeBoundaries(bounds.minY, bounds.maxY, BoundaryMode.FIXED)
    }
    binding.xyPlot.bounds.set(bounds.minX, bounds.maxX, bounds.minY, bounds.maxY)
    binding.xyPlot.outerLimits.set(bounds.minX, bounds.maxX, bounds.minY, bounds.maxY)
    setLineGraphPaddingFromBounds(
        context = context,
        binding = binding,
        bounds = bounds,
        zonedEndTime = zonedEndTime
    )
}

private fun setLineGraphPaddingFromBounds(
    context: Context,
    binding: GraphXyPlotBinding,
    bounds: RectRegion,
    zonedEndTime: ZonedDateTime
) {
    //Set up Y padding
    val minY = bounds.minY.toDouble()
    val maxY = bounds.maxY.toDouble()
    val maxBound = max(abs(minY), abs(maxY))
    val numDigits = log10(maxBound).toFloat() + 3
    binding.xyPlot.graph.paddingLeft =
        (numDigits - 1) * (context.resources.displayMetrics.scaledDensity) * 3.5f

    //Set up X padding
    val formattedTimestamp = getDateTimeFormattedForDuration(
        context = context,
        binding = binding,
        zonedEndTime = zonedEndTime
    )
    binding.xyPlot.graph.paddingBottom =
        formattedTimestamp.length * (context.resources.displayMetrics.scaledDensity)
}

private fun setXAxisFormatting(
    context: Context,
    binding: GraphXyPlotBinding,
    zonedEndTime: ZonedDateTime
) {
    binding.xyPlot.domainTitle.text = ""
    binding.xyPlot.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).format =
        object : Format() {
            override fun format(
                obj: Any,
                toAppendTo: StringBuffer,
                pos: FieldPosition
            ): StringBuffer {
                val millis = (obj as Number).toLong()
                val duration = Duration.ofMillis(millis)

                val formattedTimestamp = getDateTimeFormattedForDuration(
                    context,
                    binding,
                    duration,
                    zonedEndTime
                )
                return toAppendTo.append(formattedTimestamp)
            }

            override fun parseObject(source: String, pos: ParsePosition) = null
        }
}

val acceptableXAxisIntervals = listOf(
    1000L,//Seconds
    60_000L,//Minutes
    60* 60 * 1000L,//Hours
    2 * 60 * 60 * 1000L,//2 Hours
    3 * 60 * 60 * 1000L,//3 Hours
    4 * 60 * 60 * 1000L,//4 Hours
    5 * 60 * 60 * 1000L,//5 Hours
    6 * 60 * 60 * 1000L,//6 Hours
    8 * 60 * 60 * 1000L,//8 Hours
    12 * 60 * 60 * 1000L,//12 Hours
    24 * 60 * 60 * 1000L,//Days
    7 * 24 * 60 * 60 * 1000L,//Weeks
)

private fun setXNumLabels(
    context: Context,
    binding: GraphXyPlotBinding,
    bounds: RectRegion
) {
    val displayMetrics = context.resources.displayMetrics
    val dpWidth = binding.xyPlot.width / displayMetrics.density
    val maxLabels = (dpWidth / 30.0).toInt().toLong()

    println("samsam: dpWidth: $dpWidth, maxLabels: $maxLabels, density: ${displayMetrics.density}")

    val domainSize = bounds.maxX.toLong() - bounds.minX.toLong()

    if (maxLabels < 1 || domainSize < 1000) {
        binding.xyPlot.setDomainStep(StepMode.SUBDIVIDE, 11.0)
        return
    }

    //TODO comment this to explain it
    //TODO this is not yet correct. Although the intervals should hypothetically be correct
    // the markers will not necessarily fall on good places (e.g. 12:00 exactly) .. To do this
    // you also need to calculate an offset that needs to be added to say the first one such that
    // each marker there after will fall on a good place.
    //TODO also tbh it would be reeeeally nice to support months and even years. These get tricky though
    // because they are periods not durations so you'd need some custom stepping logic as it's not always
    // the same
    val interval = if (domainSize / maxLabels > acceptableXAxisIntervals.last()) {
        acceptableXAxisIntervals.last() * (domainSize / (maxLabels * acceptableXAxisIntervals.last()))
    } else {
        acceptableXAxisIntervals.first { (domainSize / it) < maxLabels }
    }

    println("samsam using interval: $interval")

    binding.xyPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, interval.toDouble())
}

private fun getDateTimeFormattedForDuration(
    context: Context,
    binding: GraphXyPlotBinding,
    duration: Duration = Duration.ZERO,
    zonedEndTime: ZonedDateTime
): String {
    val timestamp = zonedEndTime.plus(duration)
    val minX = binding.xyPlot.bounds.minX
    val maxX = binding.xyPlot.bounds.maxX
    if (minX == null || maxX == null) return formatDayMonth(context, timestamp)
    val durationRange = Duration.ofMillis(abs(maxX.toLong() - minX.toLong()))
    return when {
        durationRange.toMinutes() < 5L -> lineGraphHourMinuteSecondFormat.format(timestamp)
        durationRange.toDays() >= 304 -> formatMonthYear(context, timestamp)
        durationRange.toDays() >= 1 -> formatDayMonth(context, timestamp)
        else -> lineGraphHoursDateFormat.format(timestamp)
    }
}

private fun drawLineGraphFeatures(
    context: Context,
    binding: GraphXyPlotBinding,
    plottableData: Map<LineGraphFeature, FastXYSeries?>,
    listMode: Boolean
) {
    for (kvp in plottableData) {
        kvp.value?.let {
            addSeries(
                context = context,
                binding = binding,
                series = it,
                lineGraphFeature = kvp.key,
                listMode = listMode
            )
        }
    }
}

private fun addSeries(
    context: Context,
    binding: GraphXyPlotBinding,
    series: FastXYSeries,
    lineGraphFeature: LineGraphFeature,
    listMode: Boolean
) {
    val seriesFormat =
        if (listMode && lineGraphFeature.pointStyle != LineGraphPointStyle.CIRCLES_AND_NUMBERS)
            getFastLineAndPointFormatter(context, lineGraphFeature)
        else getLineAndPointFormatter(context, lineGraphFeature)
    binding.xyPlot.addSeries(series, seriesFormat)
}

private fun getLineAndPointFormatter(
    context: Context,
    lineGraphFeature: LineGraphFeature
): LineAndPointFormatter {
    val formatter = LineAndPointFormatter()
    formatter.linePaint.apply {
        color = getLinePaintColor(context, lineGraphFeature)
        strokeWidth = getLinePaintWidth(context)
    }
    getVertexPaintColor(context, lineGraphFeature)?.let {
        formatter.vertexPaint.color = it
        formatter.vertexPaint.strokeWidth = getVertexPaintWidth(context)
    } ?: run {
        formatter.vertexPaint = null
    }
    getPointLabelFormatter(context, lineGraphFeature)?.let {
        formatter.pointLabelFormatter = it
        formatter.setPointLabeler { series, index ->
            DecimalFormat("#.#").format(series.getY(index))
        }
    } ?: run {
        formatter.pointLabelFormatter = null
    }
    formatter.fillPaint = null
    return formatter
}

private fun getFastLineAndPointFormatter(
    context: Context,
    lineGraphFeature: LineGraphFeature
): LineAndPointFormatter {
    val formatter = FastLineAndPointRenderer.Formatter(
        getLinePaintColor(context, lineGraphFeature),
        getVertexPaintColor(context, lineGraphFeature),
        getPointLabelFormatter(context, lineGraphFeature)
    )
    formatter.linePaint?.apply { isAntiAlias = false }
    formatter.linePaint?.apply { strokeWidth = getLinePaintWidth(context) }
    formatter.vertexPaint?.apply { strokeWidth = getVertexPaintWidth(context) }
    return formatter
}

private fun getLinePaintWidth(
    context: Context
) = context.resources.getDimension(R.dimen.line_graph_line_thickness)

private fun getVertexPaintWidth(
    context: Context
) = context.resources.getDimension(R.dimen.line_graph_vertex_thickness)

private fun getLinePaintColor(context: Context, lineGraphFeature: LineGraphFeature): Int {
    return getPaintColor(context, lineGraphFeature)
}

private fun getVertexPaintColor(context: Context, lineGraphFeature: LineGraphFeature): Int? {
    return if (lineGraphFeature.pointStyle == LineGraphPointStyle.NONE) null
    else getPaintColor(context, lineGraphFeature)
}

private fun getPointLabelFormatter(
    context: Context,
    lineGraphFeature: LineGraphFeature
): PointLabelFormatter? {
    if (lineGraphFeature.pointStyle != LineGraphPointStyle.CIRCLES_AND_NUMBERS) return null
    val color = context.getColorFromAttr(android.R.attr.textColorPrimary)
    val pointLabelFormatter = PointLabelFormatter(
        color,
        context.resources.getDimension(R.dimen.line_graph_point_label_h_offset),
        context.resources.getDimension(R.dimen.line_graph_point_label_v_offset)
    )
    pointLabelFormatter.textPaint.textAlign = Paint.Align.RIGHT
    return pointLabelFormatter
}

private fun getPaintColor(
    context: Context,
    lineGraphFeature: LineGraphFeature
) = getColor(context, dataVisColorList[lineGraphFeature.colorIndex])

private fun getMarkerPaint(
    context: Context
): Paint {
    val color = context.getColorFromAttr(R.attr.errorTextColor)
    val paint = Paint()
    paint.color = color
    paint.strokeWidth = 2f
    return paint
}

private fun setTimeMarker(
    context: Context,
    binding: GraphXyPlotBinding,
    endTime: Temporal,
    timeMarker: OffsetDateTime?
) {
    binding.xyPlot.removeMarkers()
    if (timeMarker == null) return

    val markerPaint = getMarkerPaint(context)
    val millis = Duration.between(endTime, timeMarker).toMillis()
    binding.xyPlot.addMarker(
        XValueMarker(
            millis,
            null,
            VerticalPosition(0f, VerticalPositioning.ABSOLUTE_FROM_TOP),
            markerPaint,
            null
        )
    )
    binding.xyPlot.redraw()
}
