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
package com.samco.trackandgraph.base.database.entity.queryresponse

import androidx.room.ColumnInfo
import androidx.room.Relation
import com.samco.trackandgraph.base.database.dto.GraphEndDate
import com.samco.trackandgraph.base.database.dto.YRangeType
import com.samco.trackandgraph.base.database.entity.LineGraph
import com.samco.trackandgraph.base.database.entity.LineGraphFeature
import org.threeten.bp.temporal.TemporalAmount

internal data class LineGraphWithFeatures(
    @ColumnInfo(name = "id", index = true)
    val id: Long,

    @ColumnInfo(name = "graph_stat_id", index = true)
    val graphStatId: Long,

    @Relation(parentColumn = "id", entityColumn = "line_graph_id", entity = LineGraphFeature::class)
    val features: List<LineGraphFeature>,

    @ColumnInfo(name = "duration")
    val sampleSize: TemporalAmount?,

    @ColumnInfo(name = "y_range_type")
    val yRangeType: YRangeType,

    @ColumnInfo(name = "y_from")
    val yFrom: Double,

    @ColumnInfo(name = "y_to")
    val yTo: Double,

    @ColumnInfo(name = "end_date")
    val endDate: GraphEndDate
) {
    fun toDto() = com.samco.trackandgraph.base.database.dto.LineGraphWithFeatures(
        id,
        graphStatId,
        features.map { it.toDto() },
        sampleSize,
        yRangeType,
        yFrom,
        yTo,
        endDate
    )

    fun toLineGraph() = LineGraph(id, graphStatId, sampleSize, yRangeType, yFrom, yTo, endDate)
}