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

package com.samco.trackandgraph.functionslib

import org.threeten.bp.DayOfWeek
import org.threeten.bp.Duration
import org.threeten.bp.temporal.WeekFields
import java.util.*

interface AggregationPreferences {
    val firstDayOfWeek: DayOfWeek
    val startTimeOfDay: Duration
}

//TODO aggregation preferences are just statically coded here for now but should ultimately
// be more configurable
object GlobalAggregationPreferences : AggregationPreferences {
    override val firstDayOfWeek: DayOfWeek = getFirstDayFromSettings()
    override val startTimeOfDay: Duration = getStartTimeOfDayFromSettings()

    // we are not using settings here, so just use the values we have been using all along
    private fun getFirstDayFromSettings() = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    private fun getStartTimeOfDayFromSettings() = Duration.ofSeconds(0)
}
