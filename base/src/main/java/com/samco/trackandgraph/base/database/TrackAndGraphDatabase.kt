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
package com.samco.trackandgraph.base.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.samco.trackandgraph.base.database.dto.*
import com.samco.trackandgraph.base.database.entity.AverageTimeBetweenStat
import com.samco.trackandgraph.base.database.entity.DataPoint
import com.samco.trackandgraph.base.database.entity.Feature
import com.samco.trackandgraph.base.database.entity.GlobalNote
import com.samco.trackandgraph.base.database.entity.GraphOrStat
import com.samco.trackandgraph.base.database.entity.Group
import com.samco.trackandgraph.base.database.entity.LineGraph
import com.samco.trackandgraph.base.database.entity.LineGraphFeature
import com.samco.trackandgraph.base.database.entity.PieChart
import com.samco.trackandgraph.base.database.entity.Reminder
import com.samco.trackandgraph.base.database.entity.TimeHistogram
import com.samco.trackandgraph.base.database.entity.TimeSinceLastStat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.threeten.bp.Duration
import org.threeten.bp.LocalTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.lang.Exception

private val databaseFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

@Database(
    entities = [
        Feature::class,
        DataPoint::class,
        Group::class,
        GraphOrStat::class,
        LineGraph::class,
        AverageTimeBetweenStat::class,
        PieChart::class,
        TimeSinceLastStat::class,
        Reminder::class,
        GlobalNote::class,
        LineGraphFeature::class,
        TimeHistogram::class
    ],
    version = 46
)
@TypeConverters(Converters::class)
internal abstract class TrackAndGraphDatabase : RoomDatabase() {
    abstract val trackAndGraphDatabaseDao: TrackAndGraphDatabaseDao

    companion object {
        @Volatile
        private var INSTANCE: TrackAndGraphDatabase? = null
        fun getInstance(context: Context): TrackAndGraphDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = createRoomInstance(context)
                    INSTANCE = instance
                }
                return instance
            }
        }

        private fun createRoomInstance(context: Context): TrackAndGraphDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TrackAndGraphDatabase::class.java,
                "trackandgraph_database"
            )
                .addMigrations(*allMigrations)
                .fallbackToDestructiveMigration()
                .addCallback(databaseCallback())
                .build()
        }

        private fun databaseCallback() = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO 
                    groups_table(id, name, display_index, parent_group_id, color_index) 
                    VALUES(0, '', 0, NULL, 0)
                    """.trimMargin()
                )
            }
        }
    }
}

internal class Converters {

    private val moshi = Moshi.Builder().build()

    private fun <T> toJson(adapter: JsonAdapter<T>, value: T): String {
        return try {
            adapter.toJson(value) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun <T> fromJson(adapter: JsonAdapter<T>, value: String, onError: () -> T): T {
        return try {
            adapter.fromJson(value) ?: onError()
        } catch (e: Exception) {
            onError()
        }
    }

    @TypeConverter
    fun stringToListOfStrings(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        return fromJson(moshi.adapter(listType), value) { emptyList() }
    }

    @TypeConverter
    fun listOfStringsToString(values: List<String>): String {
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        return toJson(moshi.adapter(listType), values)
    }

    @TypeConverter
    fun stringToListOfDiscreteValues(value: String): List<DiscreteValue> {
        if (value.isBlank()) return emptyList()
        val listType = Types.newParameterizedType(List::class.java, DiscreteValue::class.java)
        return fromJson(moshi.adapter(listType), value) { emptyList() }
    }

    @TypeConverter
    fun listOfDiscreteValuesToString(values: List<DiscreteValue>): String {
        val listType = Types.newParameterizedType(List::class.java, DiscreteValue::class.java)
        return toJson(moshi.adapter(listType), values)
    }

    @TypeConverter
    fun intToFeatureType(i: Int): DataType = DataType.values()[i]

    @TypeConverter
    fun featureTypeToInt(featureType: DataType): Int = featureType.ordinal

    @TypeConverter
    fun intToGraphStatType(i: Int): GraphStatType = GraphStatType.values()[i]

    @TypeConverter
    fun graphStatTypeToInt(graphStat: GraphStatType): Int = graphStat.ordinal

    @TypeConverter
    fun stringToOffsetDateTime(value: String?): OffsetDateTime? =
        value?.let { odtFromString(value) }

    @TypeConverter
    fun offsetDateTimeToString(value: OffsetDateTime?): String =
        value?.let { stringFromOdt(it) } ?: ""

    @TypeConverter
    fun durationToString(value: Duration?): String = value?.let { value.toString() } ?: ""

    @TypeConverter
    fun stringToDuration(value: String): Duration? =
        if (value.isEmpty()) null else Duration.parse(value)

    @TypeConverter
    fun localTimeToString(value: LocalTime) = value.toString()

    @TypeConverter
    fun localTimeFromString(value: String) = LocalTime.parse(value)

    @TypeConverter
    fun checkedDaysToString(value: CheckedDays): String =
        toJson(moshi.adapter(CheckedDays::class.java), value)

    @TypeConverter
    fun checkedDaysFromString(value: String): CheckedDays =
        fromJson(moshi.adapter(CheckedDays::class.java), value) { CheckedDays.none() }

    //This use of || to concat Ints is here for legacy reasons. It avoids migrating old databases.
    // If it ain't broke don't fix it. It should probably use Moshi for consistency though.
    @TypeConverter
    fun listOfIntsToString(ints: List<Int>) = ints.joinToString("||") { it.toString() }

    @TypeConverter
    fun stringToListOfInts(intsString: String) = intsString.split("||").mapNotNull {
        it.toIntOrNull()
    }

    @TypeConverter
    fun yRangeTypeToInt(yRangeType: YRangeType) = yRangeType.ordinal

    @TypeConverter
    fun intToYRangeType(index: Int) = YRangeType.values()[index]

    @TypeConverter
    fun intToNoteType(index: Int) = NoteType.values()[index]

    @TypeConverter
    fun averagingModeToInt(averagingMode: LineGraphAveraginModes) = averagingMode.ordinal

    @TypeConverter
    fun intToAveragingMode(index: Int) = LineGraphAveraginModes.values()[index]

    @TypeConverter
    fun lineGraphPlottingModeToInt(lineGraphPlottingMode: LineGraphPlottingModes) =
        lineGraphPlottingMode.ordinal

    @TypeConverter
    fun intToLineGraphPlottingMode(index: Int) = LineGraphPlottingModes.values()[index]

    @TypeConverter
    fun lineGraphPointStyleToInt(lineGraphPointStyle: LineGraphPointStyle) =
        lineGraphPointStyle.ordinal

    @TypeConverter
    fun intToLineGraphPointStyle(index: Int) = LineGraphPointStyle.values()[index]

    @TypeConverter
    fun durationPlottingModeToInt(durationPlottingMode: DurationPlottingMode) =
        durationPlottingMode.ordinal

    @TypeConverter
    fun intToDurationPlottingMode(index: Int) = DurationPlottingMode.values()[index]

    @TypeConverter
    fun timeHistogramWindowToInt(window: TimeHistogramWindow) = window.ordinal

    @TypeConverter
    fun intToTimeHistogramWindow(index: Int) = TimeHistogramWindow.values()[index]
}

fun odtFromString(value: String): OffsetDateTime? =
    if (value.isEmpty()) null
    else databaseFormatter.parse(value, OffsetDateTime::from)

fun stringFromOdt(value: OffsetDateTime): String = databaseFormatter.format(value)

