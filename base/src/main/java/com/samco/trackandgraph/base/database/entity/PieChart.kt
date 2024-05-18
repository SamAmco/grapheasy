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
package com.samco.trackandgraph.base.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.samco.trackandgraph.base.database.dto.GraphEndDate
import com.samco.trackandgraph.base.database.dto.PieChart
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.TemporalAmount

@Entity(tableName = "pie_charts_table2",
    foreignKeys = [
        ForeignKey(
            entity = GraphOrStat::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("graph_stat_id"),
            onDelete = ForeignKey.CASCADE),
        ForeignKey(
            entity = Feature::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("feature_id"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class PieChart(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id", index = true)
    val id: Long,

    @ColumnInfo(name = "graph_stat_id", index = true)
    val graphStatId: Long,

    @ColumnInfo(name = "feature_id", index = true)
    val featureId: Long,

    @ColumnInfo(name = "duration")
    val sampleSize: TemporalAmount?,

    @ColumnInfo(name = "end_date")
    val endDate: GraphEndDate?,

    @ColumnInfo(name = "sum_by_count")
    val sumByCount: Boolean
) {
    fun toDto() = PieChart(
        id = id,
        graphStatId = graphStatId,
        featureId = featureId,
        sampleSize = sampleSize,
        endDate = endDate ?: GraphEndDate.Latest,
        sumByCount = sumByCount
    )
}