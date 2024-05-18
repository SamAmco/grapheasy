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

package com.samco.trackandgraph.functions.functions

import com.samco.trackandgraph.base.database.sampling.DataSample
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.TemporalAmount

class DataClippingFunction(
    private val endTime: OffsetDateTime?,
    private val sampleDuration: TemporalAmount?
) : DataSampleFunction {

    /**
     * Return all the data points in the sample that lie within the sampleDuration leading up to the endTime.
     * If the sampleDuration is null then all data points leading up to the end time will be returned.
     * If the endTime is null then all data points within the sampleDuration leading up to the last data point
     * (in time, first in the list) will be returned. If both the sampleDuration and endTime are null then
     * the whole list will be returned.
     */
    override suspend fun mapSample(dataSample: DataSample): DataSample {
        return DataSample.fromSequence(
            getSequence(dataSample),
            dataSample.dataSampleProperties,
            dataSample::getRawDataPoints,
            dataSample::dispose
        )
    }

    private fun getSequence(dataSample: DataSample) = sequence {
        val endOfDuration = endTime ?: (dataSample.firstOrNull() ?: return@sequence).timestamp
        val startTime =
            if (sampleDuration != null) endOfDuration.minus(sampleDuration)
            else OffsetDateTime.MIN

        yieldAll(dataSample
            .dropWhile { it.timestamp > endOfDuration }
            .takeWhile { it.timestamp >= startTime })
    }
}