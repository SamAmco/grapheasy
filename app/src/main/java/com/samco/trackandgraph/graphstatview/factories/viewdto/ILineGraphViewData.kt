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

package com.samco.trackandgraph.graphstatview.factories.viewdto

import com.androidplot.xy.FastXYSeries
import com.androidplot.xy.RectRegion
import com.androidplot.xy.StepMode
import com.samco.trackandgraph.base.database.dto.LineGraphFeature
import com.samco.trackandgraph.base.database.dto.YRangeType
import org.threeten.bp.OffsetDateTime

interface ILineGraphViewData : IGraphStatViewData {
    /**
     * Don't try to draw the graph at all if this is false. The values for everything else may
     * be invalid. This will be false if there is no data to plot.
     */
    val hasPlottableData: Boolean
        get() = false

    /**
     * True if the Y axis values represent a duration.
     */
    val durationBasedRange: Boolean
        get() = false

    /**
     * Whether the user selected dynamic or fixed range for the Y axis. This is only one factor in
     * determining the range of the Y axis. If fixed, then the range min/max can be found in [bounds].
     * Typically the only time you don't use [bounds] for min/max of the Y axis is if the graph is not
     * in list mode (i.e. full screen) and the user selected dynamic range.
     */
    val yRangeType: YRangeType
        get() = YRangeType.DYNAMIC

    /**
     * The min/max values for x and y coordinates found in all of the [plottableData] values.
     */
    val bounds: RectRegion
        get() = RectRegion()

    /**
     * The actual end time of the graph. Useful as a reference point. The X value of each point in
     * the [plottableData] series values represents the number of milliseconds offset from this point.
     * This means all the x values are likely negative but don't rely on this.
     */
    val endTime: OffsetDateTime
        get() = OffsetDateTime.MIN

    /**
     * A map of [LineGraphFeature] to [FastXYSeries] that can be plotted on the graph. The
     * line graph feature will tell you useful information like the name of the feature, and
     * the series will tell you what to plot on the graph.
     */
    val plottableData: Map<LineGraphFeature, FastXYSeries?>
        get() = emptyMap()

    /**
     * The range parameters of the Y axis. The first value is the [StepMode] and the second value
     * is the step value.
     */
    val yAxisRangeParameters: Pair<StepMode, Double>
        get() = Pair(StepMode.SUBDIVIDE, 11.0)

}