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
package com.samco.trackandgraph.base.database.dto

import com.samco.trackandgraph.base.database.entity.AverageTimeBetweenStat
import org.threeten.bp.temporal.TemporalAmount

data class AverageTimeBetweenStat(
    val id: Long,
    val graphStatId: Long,
    val featureId: Long,
    val fromValue: Double,
    val toValue: Double,
    val sampleSize: TemporalAmount?,
    val labels: List<String>,
    val endDate: GraphEndDate,
    val filterByRange: Boolean,
    val filterByLabels: Boolean
) {
    internal fun toEntity() = AverageTimeBetweenStat(
        id = id,
        graphStatId = graphStatId,
        featureId = featureId,
        fromValue = fromValue,
        toValue = toValue,
        sampleSize = sampleSize,
        labels = labels,
        endDate = endDate,
        filterByRange = filterByRange,
        filterByLabels = filterByLabels
    )
}
