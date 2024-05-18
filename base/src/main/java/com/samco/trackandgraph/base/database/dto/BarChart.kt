package com.samco.trackandgraph.base.database.dto

import com.samco.trackandgraph.base.database.entity.BarChart
import org.threeten.bp.temporal.TemporalAmount

data class BarChart(
    val id: Long,
    val graphStatId: Long,
    val featureId: Long,
    val endDate: GraphEndDate,
    val sampleSize: TemporalAmount?,
    val yRangeType: YRangeType,
    val yTo: Double,
    val scale: Double,
    val barPeriod: BarChartBarPeriod,
    val sumByCount: Boolean
) {
    internal fun toEntity() = BarChart(
        id = id,
        graphStatId = graphStatId,
        featureId = featureId,
        endDate = endDate,
        sampleSize = sampleSize,
        yRangeType = yRangeType,
        yTo = yTo,
        scale = scale,
        barPeriod = barPeriod,
        sumByCount = sumByCount
    )
}