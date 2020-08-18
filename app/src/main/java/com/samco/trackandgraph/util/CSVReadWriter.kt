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
package com.samco.trackandgraph.util

import com.samco.trackandgraph.R
import com.samco.trackandgraph.database.*
import com.samco.trackandgraph.database.entity.DataPoint
import com.samco.trackandgraph.database.entity.DiscreteValue
import com.samco.trackandgraph.database.entity.Feature
import com.samco.trackandgraph.database.entity.FeatureType
import com.samco.trackandgraph.database.entity.FeatureShowCountPeriod
import com.samco.trackandgraph.database.entity.FeatureShowCountMethod
import kotlinx.coroutines.yield
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception

object CSVReadWriter {
    enum class REQUIRED_HEADERS {
        FeatureName,
        Timestamp,
        Value
    }

    enum class HEADERS {
        FeatureName,
        Timestamp,
        Value,
        Note
    }

    suspend fun writeFeaturesToCSV(
        features: List<Feature>,
        dataSource: TrackAndGraphDatabaseDao,
        outStream: OutputStream
    ) {
        outStream.writer().use {
            val csvWriter = CSVPrinter(it, CSVFormat.DEFAULT.withHeader(HEADERS::class.java))
            for (feature in features) {
                val dataPoints = dataSource.getDataPointsForFeatureSync(feature.id)

                val dataPointToString = when (feature.featureType) {
                    FeatureType.DISCRETE -> { dp: DataPoint ->
                        DiscreteValue.fromDataPoint(dp).toString()
                    }
                    FeatureType.CONTINUOUS -> { dp: DataPoint -> dp.value.toString() }
                    FeatureType.DURATION -> { dp: DataPoint -> DataPoint.getDisplayValue(dp, FeatureType.DURATION) }
                }

                dataPoints.forEach { dp ->
                    csvWriter.printRecord(
                        feature.name,
                        dp.timestamp.toString(),
                        dataPointToString(dp),
                        dp.note
                    )
                    yield()
                }
            }
            it.flush()
        }
    }

    suspend fun readFeaturesFromCSV(
        dataSource: TrackAndGraphDatabaseDao,
        inputStream: InputStream,
        trackGroupId: Long
    ) {
        try {
            inputStream.reader().use {
                val records = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(it)
                val headerMap = records.headerMap
                validateHeaderMap(headerMap)
                ingestRecords(dataSource, records, trackGroupId)
            }
        } catch (e: Exception) {
            Timber.e(e)
            if (e is ImportFeaturesException) throw e
            else throw ImportFeaturesException(R.string.import_exception_unknown)
        }
    }

    private suspend fun ingestRecords(
        dataSource: TrackAndGraphDatabaseDao,
        records: Iterable<CSVRecord>,
        trackGroupId: Long
    ) {
        val existingFeatures = dataSource.getFeaturesForTrackGroupSync(trackGroupId).toMutableList()
        val existingFeaturesByName = existingFeatures.map { it.name to it }.toMap().toMutableMap()
        val newDataPoints = mutableListOf<DataPoint>()

        records.forEachIndexed { recordNumber, record ->
            val lineNumber = recordNumber + 2 //one for index by zero and one for headers
            val rec = getValidRecordData(record, lineNumber)
            if (existingFeaturesByName.containsKey(rec.featureName)) {
                addDataPointToExistingFeature(
                    dataSource,
                    existingFeatures,
                    existingFeaturesByName,
                    newDataPoints,
                    rec,
                    lineNumber
                )
            } else {
                addDataPointToNewFeature(
                    dataSource,
                    existingFeatures,
                    existingFeaturesByName,
                    newDataPoints,
                    trackGroupId,
                    rec,
                    lineNumber
                )
            }
            if (lineNumber % 1000 == 0) {
                dataSource.insertDataPoints(newDataPoints)
                newDataPoints.clear()
            }
            yield()
        }
        if (newDataPoints.isNotEmpty()) dataSource.insertDataPoints(newDataPoints)
    }

    private fun addDataPointToNewFeature(
        dataSource: TrackAndGraphDatabaseDao,
        existingFeatures: MutableList<Feature>,
        existingFeaturesByName: MutableMap<String, Feature>,
        points: MutableList<DataPoint>,
        trackGroupId: Long,
        rec: RecordData,
        lineNumber: Int
    ) {
        val newFeature = createNewFeature(dataSource, rec, trackGroupId, lineNumber)
        insertFeature(existingFeatures, existingFeaturesByName, newFeature)
        when (newFeature.featureType) {
            FeatureType.DISCRETE -> addDiscreteDataPoint(
                points,
                rec.value,
                rec.timestamp,
                newFeature,
                rec.note,
                lineNumber
            )
            FeatureType.CONTINUOUS -> addContinuousDataPoint(
                points,
                rec.value.toDoubleOrNull(),
                rec.timestamp,
                newFeature.id,
                rec.note
            )
            FeatureType.DURATION -> addDurationDataPoint(
                points,
                rec.value,
                rec.timestamp,
                newFeature.id,
                rec.note
            )
        }
    }

    private fun addDataPointToExistingFeature(
        dataSource: TrackAndGraphDatabaseDao,
        existingFeatures: MutableList<Feature>,
        existingFeaturesByName: MutableMap<String, Feature>,
        points: MutableList<DataPoint>,
        rec: RecordData,
        lineNumber: Int
    ) {
        val feature = existingFeaturesByName[rec.featureName]!!
        when (getFeatureTypeForValue(rec.value)) {
            FeatureType.DISCRETE -> {
                validateFeatureType(feature, FeatureType.DISCRETE, lineNumber)
                val discreteValue =
                    addDiscreteDataPoint(
                        points,
                        rec.value,
                        rec.timestamp,
                        feature,
                        rec.note,
                        lineNumber
                    )
                if (!feature.discreteValues.contains(discreteValue)) {
                    addDiscreteValueToFeature(
                        dataSource,
                        existingFeatures,
                        existingFeaturesByName,
                        feature,
                        discreteValue,
                        lineNumber
                    )
                }
            }
            FeatureType.CONTINUOUS -> {
                validateFeatureType(feature, FeatureType.CONTINUOUS, lineNumber)
                addContinuousDataPoint(
                    points,
                    rec.value.toDoubleOrNull(),
                    rec.timestamp,
                    feature.id,
                    rec.note
                )
            }
            FeatureType.DURATION -> {
                validateFeatureType(feature, FeatureType.DURATION, lineNumber)
                addDurationDataPoint(
                    points,
                    rec.value,
                    rec.timestamp,
                    feature.id,
                    rec.note
                )
            }
        }
    }

    private fun insertFeature(
        existingFeatures: MutableList<Feature>,
        existingFeaturesByName: MutableMap<String, Feature>,
        feature: Feature
    ) {
        existingFeatures.add(feature)
        existingFeaturesByName[feature.name] = feature
    }

    private fun validateNotDiscreteValueConflict(
        discreteValue: DiscreteValue,
        feature: Feature,
        lineNumber: Int
    ) {
        if (feature.discreteValues.any { dv -> dv.index == discreteValue.index }) {
            throw ImportFeaturesException(
                R.string.import_exception_discrete_value_conflict,
                listOf(lineNumber.toString())
            )
        }
    }

    private fun addDiscreteValueToFeature(
        dataSource: TrackAndGraphDatabaseDao,
        existingFeatures: MutableList<Feature>,
        existingFeaturesByName: MutableMap<String, Feature>,
        feature: Feature,
        discreteValue: DiscreteValue,
        lineNumber: Int
    ) {
        validateNotDiscreteValueConflict(discreteValue, feature, lineNumber)
        existingFeatures.remove(feature)
        existingFeaturesByName.remove(feature.name)
        val newFeature =
            tryAddDiscreteValueToFeature(dataSource, feature, discreteValue, lineNumber)
        existingFeatures.add(newFeature)
        existingFeaturesByName[newFeature.name] = newFeature
    }

    private fun addDiscreteDataPoint(
        points: MutableList<DataPoint>,
        value: String,
        timestamp: OffsetDateTime,
        feature: Feature,
        note: String,
        lineNumber: Int
    ): DiscreteValue {
        val discreteValue = tryGetDiscreteValueFromString(value, lineNumber)
        points.add(
            DataPoint(
                timestamp,
                feature.id,
                discreteValue.index.toDouble(),
                discreteValue.label,
                note
            )
        )
        return discreteValue
    }

    private fun addContinuousDataPoint(
        points: MutableList<DataPoint>,
        value: Double?,
        timestamp: OffsetDateTime,
        featureId: Long,
        note: String
    ) {
        points.add(
            DataPoint(
                timestamp,
                featureId,
                value ?: 1.0,
                "",
                note
            )
        )
    }


    private fun addDurationDataPoint(
        points: MutableList<DataPoint>,
        value: String,
        timestamp: OffsetDateTime,
        featureId: Long,
        note: String
    ) {
        val segments = value.split(":")
        val hours = segments.getOrNull(0)?.toLong() ?: 0L
        val minutes = segments.getOrNull(1)?.toLong() ?: 0L
        val seconds = segments.getOrNull(2)?.toLong() ?: 0L
        val totalSeconds = Duration.ZERO
            .plusHours(hours)
            .plusMinutes(minutes)
            .plusSeconds(seconds)
            .seconds
            .toDouble()
        points.add(
            DataPoint(
                timestamp,
                featureId,
                totalSeconds,
                "",
                note
            )
        )
    }

    private fun createNewFeature(
        dataSource: TrackAndGraphDatabaseDao,
        rec: RecordData,
        trackGroupId: Long,
        lineNumber: Int
    ): Feature {
        if (rec.featureName.length > MAX_FEATURE_NAME_LENGTH) throw ImportFeaturesException(
            R.string.import_exception_feature_name_too_long,
            listOf(lineNumber.toString())
        )
        val featureType = getFeatureTypeForValue(rec.value)
        val discreteValues =
            if (featureType == FeatureType.DISCRETE) listOf(
                tryGetDiscreteValueFromString(
                    rec.value,
                    lineNumber
                )
            )
            else listOf()
        val newFeature = Feature.create(
            0, rec.featureName, trackGroupId, featureType,
            discreteValues, false, 1.0, 0, "",
            FeatureShowCountPeriod.ALL, FeatureShowCountMethod.COUNT_ENTRIES
        )
        val featureId = dataSource.insertFeature(newFeature)
        return newFeature.copy(id = featureId)
    }

    private fun tryGetDiscreteValueFromString(string: String, lineNumber: Int): DiscreteValue {
        try {
            return DiscreteValue.fromString(string)
        } catch (e: Exception) {
            throw ImportFeaturesException(
                R.string.import_exception_bad_discrete_value,
                listOf(lineNumber.toString())
            )
        }
    }

    private fun tryAddDiscreteValueToFeature(
        dataSource: TrackAndGraphDatabaseDao,
        feature: Feature,
        discreteValue: DiscreteValue,
        lineNumber: Int
    ): Feature {
        val newDiscreteValues = feature.discreteValues.toMutableList()
        if (newDiscreteValues.size == MAX_DISCRETE_VALUES_PER_FEATURE)
            throw ImportFeaturesException(
                R.string.import_exception_max_discrete_values_reached,
                listOf(MAX_DISCRETE_VALUES_PER_FEATURE.toString())
            )
        if (newDiscreteValues.map { dv -> dv.index }.contains(discreteValue.index))
            throw ImportFeaturesException(
                R.string.import_exception_discrete_value_index_exists,
                listOf(lineNumber.toString(), discreteValue.index.toString())
            )
        if (newDiscreteValues.map { dv -> dv.label }.contains(discreteValue.label))
            throw ImportFeaturesException(
                R.string.import_exception_discrete_value_label_exists,
                listOf(lineNumber.toString(), discreteValue.label)
            )
        newDiscreteValues.add(discreteValue)
        dataSource.updateFeature(feature.copy(discreteValues = newDiscreteValues))
        return feature.copy(discreteValues = newDiscreteValues)
    }

    private fun validateFeatureType(feature: Feature, expectedType: FeatureType, lineNumber: Int) {
        if (feature.featureType != expectedType)
            throw ImportFeaturesException(
                R.string.import_exception_inconsistent_data_type,
                listOf(lineNumber.toString())
            )
    }

    private fun getFeatureTypeForValue(value: String): FeatureType {
        val durationRegex = Regex("\\d*:\\d{2}:\\d{2}")
        return when {
            value.matches(durationRegex) -> FeatureType.DURATION
            value.contains(":") -> FeatureType.DISCRETE
            else -> FeatureType.CONTINUOUS
        }
    }

    class ImportFeaturesException(val stringId: Int, val stringArgs: List<String>? = null) :
        Exception()

    private data class RecordData(
        val featureName: String,
        val value: String,
        val timestamp: OffsetDateTime,
        val note: String
    )

    private fun getValidRecordData(record: CSVRecord, lineNumber: Int): RecordData {
        val featureName = record.get(HEADERS.FeatureName)
            ?: throw ImportFeaturesException(
                R.string.import_exception_inconsistent_record,
                listOf(lineNumber.toString())
            )
        val timestamp = record.get(HEADERS.Timestamp)
            ?: throw ImportFeaturesException(
                R.string.import_exception_inconsistent_record,
                listOf(lineNumber.toString())
            )
        val value = record.get(HEADERS.Value)
            ?: throw ImportFeaturesException(
                R.string.import_exception_inconsistent_record,
                listOf(lineNumber.toString())
            )
        val note = if (record.isMapped(HEADERS.Note.name)) record.get(HEADERS.Note) else ""
        lateinit var parsedTimestamp: OffsetDateTime
        try {
            parsedTimestamp = odtFromString(timestamp)!!
        } catch (_: Exception) {
            throw ImportFeaturesException(
                R.string.import_exception_bad_timestamp,
                listOf(lineNumber.toString())
            )
        }
        return RecordData(featureName, value, parsedTimestamp, note)
    }

    private fun validateHeaderMap(headerIndexes: Map<String, Int>) {
        val ourHeaders = REQUIRED_HEADERS.values().map { v -> v.name }
        val exception = ImportFeaturesException(
            R.string.import_exception_bad_headers,
            listOf(ourHeaders.joinToString())
        )
        REQUIRED_HEADERS.values().forEach {
            if (!headerIndexes.containsKey(it.name)) throw exception
        }
    }
}
