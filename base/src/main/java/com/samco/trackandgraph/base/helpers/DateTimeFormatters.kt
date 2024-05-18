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

package com.samco.trackandgraph.base.helpers

import android.content.Context
import android.text.format.DateUtils
import com.samco.trackandgraph.base.R
import com.samco.trackandgraph.base.database.dto.DataPoint
import com.samco.trackandgraph.base.database.dto.IDataPoint
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.Temporal
import java.text.DecimalFormat


//TODO should really refactor all this stuff into classes and interfaces

val doubleFormatter = DecimalFormat("#.##################")

enum class DateFormatSetting { DMY, MDY, YMD }

private fun getDateTimePref(context: Context): DateFormatSetting {
    val datePrefIndex = getPrefs(context).getInt(
        DATE_FORMAT_SETTING_PREF_KEY, DateFormatSetting.DMY.ordinal
    )
    return DateFormatSetting.values()[datePrefIndex]
}

private fun formatDate(formatter: DateTimeFormatter, date: Temporal): String =
    formatter.format(date)

private fun formatDate(format: String, date: Temporal): String = DateTimeFormatter
    .ofPattern(format)
    .format(date)

fun getWeekDayNames(context: Context) = listOf(
    context.getString(R.string.mon),
    context.getString(R.string.tue),
    context.getString(R.string.wed),
    context.getString(R.string.thu),
    context.getString(R.string.fri),
    context.getString(R.string.sat),
    context.getString(R.string.sun)
)

/**
 * Should return the day of the week locally at the time the data point was tracked
 */
private fun weekDayPart(dateTime: OffsetDateTime, weekDayNames: List<String>) =
    "(${weekDayNames[dateTime.dayOfWeek.value - 1]})"

fun formatDayMonthYearHourMinuteWeekDayOneLine(
    context: Context,
    weekDayNames: List<String>,
    dateTime: OffsetDateTime
) = formatDayMonthYear(context, weekDayNames, dateTime) +
        "  " +
        formatHourMinuteSecondAndOffset(dateTime)

fun DataPoint.getDisplayValue(isDuration: Boolean): String {
    val time = this.timestamp
    val value = this.value
    val label = this.label
    return object : IDataPoint() {
        override val timestamp = time
        override val value = value
        override val label = label
    }.getDisplayValue(isDuration)
}

fun IDataPoint.getDisplayValue(isDuration: Boolean): String {
    return when {
        isDuration -> formatTimeDuration(value.toLong())
        label.isNotEmpty() -> doubleFormatter.format(value)
        else -> doubleFormatter.format(value)
    }.let {
        if (label.isNotEmpty()) "$it : $label"
        else it
    }
}

fun formatDayMonthYearHourMinuteWeekDayTwoLines(
    context: Context,
    weekDayNames: List<String>,
    dateTime: OffsetDateTime
) = formatDayMonthYear(context, weekDayNames, dateTime) +
        "\n" +
        formatHourMinuteSecondAndOffset(dateTime)

fun formatHourMinuteSecondAndOffset(dateTime: OffsetDateTime): String {
    val stringBuilder = StringBuilder()
    stringBuilder.append(formatHourMinute(dateTime))

    val offsetDiff = OffsetDateTime.now().offset.totalSeconds - dateTime.offset.totalSeconds
    val offsetDiffHours = offsetDiff / 3600
    if (offsetDiffHours != 0) {
        stringBuilder.append(" (")
        if (offsetDiffHours > 0) stringBuilder.append("+")
        stringBuilder.append(offsetDiffHours)
        stringBuilder.append(")")
    }
    return stringBuilder.toString()

}

fun formatDayMonthYear(
    context: Context,
    weekDayNames: List<String>,
    dateTime: OffsetDateTime
): String {
    val stringBuilder = StringBuilder()
    stringBuilder.append(formatDayMonthYear(context, dateTime))
    stringBuilder.append(" ")
    stringBuilder.append(weekDayPart(dateTime, weekDayNames))
    return stringBuilder.toString()
}

fun formatHourMinute(temporal: Temporal) = formatDate("HH:mm", temporal)

fun formatDayMonthYearHourMinute(context: Context, temporal: Temporal): String {
    val format = when (getDateTimePref(context)) {
        DateFormatSetting.DMY -> "dd/MM/yy  HH:mm"
        DateFormatSetting.MDY -> "MM/dd/yy  HH:mm"
        DateFormatSetting.YMD -> "yy/MM/dd  HH:mm"
    }
    return formatDate(format, temporal)
}

fun formatDayMonthYear(context: Context, temporal: Temporal): String {
    val format = when (getDateTimePref(context)) {
        DateFormatSetting.DMY -> "dd/MM/yy"
        DateFormatSetting.MDY -> "MM/dd/yy"
        DateFormatSetting.YMD -> "yy/MM/dd"
    }
    return formatDate(format, temporal)
}

fun getDayMonthFormatter(context: Context): DateTimeFormatter {
    val format = when (getDateTimePref(context)) {
        DateFormatSetting.DMY -> "dd/MM"
        DateFormatSetting.MDY, DateFormatSetting.YMD -> "MM/dd"
    }
    return DateTimeFormatter.ofPattern(format)
}

fun formatDayMonth(context: Context, temporal: Temporal): String {
    return formatDate(getDayMonthFormatter(context), temporal)
}

fun getMonthYearFormatter(context: Context): DateTimeFormatter {
    val format = when (getDateTimePref(context)) {
        DateFormatSetting.DMY, DateFormatSetting.MDY -> "MM/yy"
        DateFormatSetting.YMD -> "yy/MM"
    }
    return DateTimeFormatter.ofPattern(format)
}

fun formatMonthYear(context: Context, temporal: Temporal): String {
    return formatDate(getMonthYearFormatter(context), temporal)
}

fun formatTimeDuration(seconds: Long): String {
    val absSecs = kotlin.math.abs(seconds)
    val timeStr =
        String.format("%d:%02d:%02d", absSecs / 3600, (absSecs % 3600) / 60, (absSecs % 60))
    if (seconds < 0) return "-$timeStr"
    return timeStr
}

fun formatTimeToDaysHoursMinutesSeconds(
    context: Context,
    millis: Long,
    twoLines: Boolean = true
): String {
    val totalSeconds = millis / 1000
    val daysNum = (totalSeconds / 86400).toInt()
    val days = daysNum.toString()
    val hours = ((totalSeconds % 86400) / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    val hoursStr = "%02d".format(((totalSeconds % 86400) / 3600).toInt())
    val minutesStr = "%02d".format(((totalSeconds % 3600) / 60).toInt())
    val secondsStr = "%02d".format((totalSeconds % 60).toInt())
    val hasHms = (hours + minutes + seconds) > 0
    val hms = "$hoursStr:$minutesStr:$secondsStr"

    return StringBuilder().apply {
        if (daysNum == 0) append(hms)
        else {
            val daysSuffix =
                if (daysNum == 1) context.getString(R.string.day)
                else context.getString(R.string.days)

            append("$days $daysSuffix")

            if (hasHms) {
                if (twoLines) appendLine()
                else append(", ")
                append(hms)
            }
        }
    }.toString()
}

fun formatRelativeTimeSpan(
    context: Context,
    dateTime: OffsetDateTime
): String {
    //There's a special case here where the time since is less than a day and more than an hour ago
    //because DateUtils.getRelativeTimeSpanString() will not mention how many minutes ago it was. So
    //it would just say 1 hour ago even if it was 1 hour and 59 minutes ago.
    //DateUtils.getRelativeTimeSpanString() still does most of the logic though for the various
    //edge cases and across locales
    val duration = Duration.between(dateTime, OffsetDateTime.now())
    return if (!duration.isNegative && duration.toDays() < 1 && duration.toMinutes() > 60) {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        context.getString(R.string.hours_and_minutes_ago, hours, minutes)
    } else DateUtils.getRelativeTimeSpanString(dateTime.toInstant().toEpochMilli()).toString()
}