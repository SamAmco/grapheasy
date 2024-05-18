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

package com.samco.trackandgraph

import com.samco.trackandgraph.base.database.dto.CheckedDays
import com.samco.trackandgraph.base.database.dto.DataType
import com.samco.trackandgraph.base.database.dto.DurationPlottingMode
import com.samco.trackandgraph.base.database.dto.GraphEndDate
import com.samco.trackandgraph.base.database.dto.GraphOrStat
import com.samco.trackandgraph.base.database.dto.GraphStatType
import com.samco.trackandgraph.base.database.dto.LastValueStat
import com.samco.trackandgraph.base.database.dto.LineGraphAveraginModes
import com.samco.trackandgraph.base.database.dto.LineGraphFeature
import com.samco.trackandgraph.base.database.dto.LineGraphPlottingModes
import com.samco.trackandgraph.base.database.dto.LineGraphPointStyle
import com.samco.trackandgraph.base.database.dto.LineGraphWithFeatures
import com.samco.trackandgraph.base.database.dto.PieChart
import com.samco.trackandgraph.base.database.dto.Reminder
import com.samco.trackandgraph.base.database.dto.TimeHistogram
import com.samco.trackandgraph.base.database.dto.TimeHistogramWindow
import com.samco.trackandgraph.base.database.dto.TrackerSuggestionType
import com.samco.trackandgraph.base.database.dto.YRangeType
import com.samco.trackandgraph.base.model.DataInteractor
import org.threeten.bp.Duration
import org.threeten.bp.LocalTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.Period

suspend fun createScreenshotsGroup(dataInteractor: DataInteractor) {
    val outerGroupId = dataInteractor.insertGroup(createGroup("Screenshots"))
    createGroupListForScreenshots(dataInteractor, outerGroupId)
    createDailyGroup(dataInteractor, outerGroupId)
    createExerciseGroup(dataInteractor, outerGroupId)
    createRestDaysGroup(dataInteractor, outerGroupId)
    createReminders(dataInteractor)
}

private suspend fun createReminders(dataInteractor: DataInteractor) {
    dataInteractor.updateReminders(
        listOf(
            Reminder(
                1L,
                0,
                "Weight",
                time = LocalTime.of(17, 0),
                checkedDays = CheckedDays(
                    monday = false,
                    tuesday = false,
                    wednesday = true,
                    thursday = false,
                    friday = false,
                    saturday = false,
                    sunday = true
                )
            ),
            Reminder(
                2L,
                1,
                "Tracking dailies",
                time = LocalTime.of(22, 0),
                checkedDays = CheckedDays(
                    monday = true,
                    tuesday = true,
                    wednesday = true,
                    thursday = true,
                    friday = true,
                    saturday = true,
                    sunday = true
                )
            )
        )
    )
}

private suspend fun createRestDaysGroup(dataInteractor: DataInteractor, parent: Long) {
    val stressTracker = dataInteractor.insertTracker(createTracker("Stress", parent))
    val stressFeatureId = dataInteractor.getTrackerById(stressTracker)!!.featureId

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = stressTracker,
        sinTransform = SinTransform(3.0, 21.0, -1.0, 8.0),
        randomOffsetScalar = 1.0,
        spacing = Duration.ofDays(1),
        spacingRandomisationHours = 4,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1),
        roundToInt = true,
        clampMin = 0.0,
        clampMax = 3.0,
        labels = listOf("None", "Low", "Medium", "High")
    )

    val dayOffTracker = dataInteractor.insertTracker(createTracker("Day off", parent))
    val dayOffFeatureId = dataInteractor.getTrackerById(dayOffTracker)!!.featureId

    dataInteractor.insertDataPoint(
        createDataPoint(
            timestamp = OffsetDateTime.now().minusDays(2).minusHours(2).minusMinutes(43),
            featureId = dayOffFeatureId,
            value = 1.0,
        )
    )

    dataInteractor.insertGroup(
        createGroup(name = "Rest day statistics", parentGroupId = parent)
    ).let {
        createStressPieChart(dataInteractor, stressFeatureId, it)
        createTimeSinceDayOff(dataInteractor, dayOffFeatureId, it)
        createStressfulDaysHistogram(dataInteractor, stressFeatureId, it)
    }
}

private suspend fun createStressfulDaysHistogram(
    dataInteractor: DataInteractor,
    stressFeatureId: Long,
    parent: Long
) {
    val graphStat = GraphOrStat(
        id = 0L,
        groupId = parent,
        name = "Most stressful days",
        type = GraphStatType.TIME_HISTOGRAM,
        displayIndex = 2
    )

    val timeHistogram = TimeHistogram(
        id = 0L,
        graphStatId = 0L,
        featureId = stressFeatureId,
        sampleSize = null,
        window = TimeHistogramWindow.WEEK,
        sumByCount = false,
        endDate = GraphEndDate.Latest
    )

    dataInteractor.insertTimeHistogram(graphStat, timeHistogram)
}

private suspend fun createTimeSinceDayOff(
    dataInteractor: DataInteractor,
    dayOffFeatureId: Long,
    parent: Long
) {
    val graphStat = GraphOrStat(
        id = 0L,
        groupId = parent,
        name = "Time since taking a day off",
        type = GraphStatType.LAST_VALUE,
        displayIndex = 1
    )

    val lastValueStat = LastValueStat(
        id = 0L,
        graphStatId = 0L,
        featureId = dayOffFeatureId,
        endDate = GraphEndDate.Latest,
        fromValue = 0.0,
        toValue = 1.0,
        labels = emptyList(),
        filterByRange = false,
        filterByLabels = false
    )

    dataInteractor.insertLastValueStat(graphStat, lastValueStat)
}

private suspend fun createStressPieChart(
    dataInteractor: DataInteractor,
    stressFeatureId: Long,
    parent: Long
) {
    val graphStat = GraphOrStat(
        id = 0L,
        groupId = parent,
        name = "Stress pie chart",
        type = GraphStatType.PIE_CHART,
        displayIndex = 0
    )

    val pieChart = PieChart(
        id = 0L,
        graphStatId = 0L,
        featureId = stressFeatureId,
        sampleSize = null,
        endDate = GraphEndDate.Latest,
        sumByCount = true
    )

    dataInteractor.insertPieChart(graphStat, pieChart)
}

private suspend fun createGroupListForScreenshots(dataInteractor: DataInteractor, parent: Long) {
    val groupListGroup = dataInteractor.insertGroup(
        createGroup(name = "Track & Graph                              Groups list", parentGroupId = parent)
    )

    dataInteractor.insertGroup(
        createGroup(
            name = "Meal time tracking",
            displayIndex = 1,
            parentGroupId = groupListGroup,
            colorIndex = 11
        )
    )
    dataInteractor.insertGroup(
        createGroup(
            name = "Morning tracking",
            displayIndex = 2,
            parentGroupId = groupListGroup,
            colorIndex = 6
        )
    )
    dataInteractor.insertGroup(
        createGroup(
            name = "Daily tracking",
            displayIndex = 3,
            parentGroupId = groupListGroup,
            colorIndex = 0
        )
    )
    dataInteractor.insertGroup(
        createGroup(
            name = "Weekly tracking",
            displayIndex = 4,
            parentGroupId = groupListGroup,
            colorIndex = 2
        )
    )
    dataInteractor.insertGroup(
        createGroup(
            name = "Exercise routine tracking",
            displayIndex = 5,
            parentGroupId = groupListGroup,
            colorIndex = 8
        )
    )
    dataInteractor.insertGroup(
        createGroup(
            name = "Weight loss graphs",
            displayIndex = 6,
            parentGroupId = groupListGroup,
            colorIndex = 7
        )
    )
    dataInteractor.insertGroup(
        createGroup(
            name = "Mood quality",
            displayIndex = 7,
            parentGroupId = groupListGroup,
            colorIndex = 3
        )
    )
    dataInteractor.insertGroup(
        createGroup(
            name = "Stress and rest statistics",
            displayIndex = 8,
            parentGroupId = groupListGroup,
            colorIndex = 4
        )
    )
}

private suspend fun createExerciseGroup(dataInteractor: DataInteractor, parent: Long) {
    val exerciseTracker = dataInteractor.insertTracker(
        createTracker(name = "Exercise", groupId = parent)
    )
    val exerciseFeatureId = dataInteractor.getTrackerById(exerciseTracker)!!.featureId

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = exerciseTracker,
        sinTransform = SinTransform(amplitude = 1.0, wavelength = 5.0, yOffset = -1.0),
        randomOffsetScalar = 1.0,
        roundToInt = true,
        clampMin = 0.0,
        clampMax = 1.0,
    )

    val illnessTracker = dataInteractor.insertTracker(
        createTracker(name = "Sick day (weekly)", groupId = parent)
    )
    val illnessFeatureId = dataInteractor.getTrackerById(illnessTracker)!!.featureId

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = illnessTracker,
        sinTransform = SinTransform(
            amplitude = 3.0,
            wavelength = 160.0,
            yOffset = -2.0,
            xOffset = 80.0
        ),
        randomOffsetScalar = 1.0,
        roundToInt = true,
        clampMin = 0.0,
        clampMax = 7.0,
    )

    dataInteractor.insertGroup(createGroup("Exercise", parentGroupId = parent)).let {
        createExerciseGraph1(
            dataInteractor = dataInteractor,
            exerciseFeatureId = exerciseFeatureId,
            illnessFeatureId = illnessFeatureId,
            parent = it
        )
        createExerciseGraph2(
            dataInteractor = dataInteractor,
            exerciseFeatureId = exerciseFeatureId,
            parent = it
        )
    }
}

private suspend fun createExerciseGraph2(
    dataInteractor: DataInteractor,
    exerciseFeatureId: Long,
    parent: Long
) {
    val graphStat = GraphOrStat(
        id = 0L,
        groupId = parent,
        name = "Exercise weekly totals in the last 6 months",
        type = GraphStatType.LINE_GRAPH,
        displayIndex = 0
    )

    val lineGraph = LineGraphWithFeatures(
        id = 0L,
        graphStatId = 0L,
        features = listOf(
            LineGraphFeature(
                id = 0L,
                lineGraphId = 0L,
                featureId = exerciseFeatureId,
                name = "Exercise",
                colorIndex = 0,
                averagingMode = LineGraphAveraginModes.NO_AVERAGING,
                plottingMode = LineGraphPlottingModes.GENERATE_WEEKLY_TOTALS,
                pointStyle = LineGraphPointStyle.CIRCLES_AND_NUMBERS,
                offset = 0.0,
                scale = 1.0,
                durationPlottingMode = DurationPlottingMode.NONE
            )
        ),
        sampleSize = Period.ofMonths(6),
        yRangeType = YRangeType.DYNAMIC,
        yFrom = 0.0,
        yTo = 0.0,
        endDate = GraphEndDate.Latest
    )

    dataInteractor.insertLineGraph(graphStat, lineGraph)
}

private suspend fun createExerciseGraph1(
    dataInteractor: DataInteractor,
    exerciseFeatureId: Long,
    illnessFeatureId: Long,
    parent: Long
) {
    val graphStat = GraphOrStat(
        id = 0L,
        groupId = parent,
        name = "Exercise Vs illness moving averages in the last 6 months",
        type = GraphStatType.LINE_GRAPH,
        displayIndex = 1
    )

    val lineGraph = LineGraphWithFeatures(
        id = 0L,
        graphStatId = 0L,
        features = listOf(
            LineGraphFeature(
                id = 0L,
                lineGraphId = 0L,
                featureId = exerciseFeatureId,
                name = "Weekly",
                colorIndex = 7,
                averagingMode = LineGraphAveraginModes.WEEKLY_MOVING_AVERAGE,
                plottingMode = LineGraphPlottingModes.WHEN_TRACKED,
                pointStyle = LineGraphPointStyle.NONE,
                offset = 0.0,
                scale = 1.0,
                durationPlottingMode = DurationPlottingMode.NONE
            ),
            LineGraphFeature(
                id = 0L,
                lineGraphId = 0L,
                featureId = exerciseFeatureId,
                name = "Monthly",
                colorIndex = 0,
                averagingMode = LineGraphAveraginModes.MONTHLY_MOVING_AVERAGE,
                plottingMode = LineGraphPlottingModes.WHEN_TRACKED,
                pointStyle = LineGraphPointStyle.NONE,
                offset = 0.0,
                scale = 1.0,
                durationPlottingMode = DurationPlottingMode.NONE
            ),
            LineGraphFeature(
                id = 0L,
                lineGraphId = 0L,
                featureId = exerciseFeatureId,
                name = "Yearly",
                colorIndex = 4,
                averagingMode = LineGraphAveraginModes.YEARLY_MOVING_AVERAGE,
                plottingMode = LineGraphPlottingModes.WHEN_TRACKED,
                pointStyle = LineGraphPointStyle.NONE,
                offset = 0.0,
                scale = 1.0,
                durationPlottingMode = DurationPlottingMode.NONE
            ),
            LineGraphFeature(
                id = 0L,
                lineGraphId = 0L,
                featureId = illnessFeatureId,
                name = "Sick day (weekly)",
                colorIndex = 11,
                averagingMode = LineGraphAveraginModes.WEEKLY_MOVING_AVERAGE,
                plottingMode = LineGraphPlottingModes.WHEN_TRACKED,
                pointStyle = LineGraphPointStyle.NONE,
                offset = 0.0,
                scale = 0.1,
                durationPlottingMode = DurationPlottingMode.NONE
            ),
        ),
        sampleSize = Period.ofMonths(6),
        yRangeType = YRangeType.DYNAMIC,
        yFrom = 0.0,
        yTo = 0.0,
        endDate = GraphEndDate.Latest
    )

    dataInteractor.insertLineGraph(graphStat, lineGraph)
}

private suspend fun createDailyGroup(dataInteractor: DataInteractor, parent: Long) {
    dataInteractor.insertGroup(createGroup("Daily", parentGroupId = parent)).let {
        createSleepTracker(dataInteractor, it)
        createProductivityTracker(dataInteractor, it)
        createAlcoholTracker(dataInteractor, it)
        createMeditationTracker(dataInteractor, it)
        createWorkTracker(dataInteractor, it)
        createWeightTracker(dataInteractor, it)
        createExerciseTracker(dataInteractor, it)
        createStudyingTracker(dataInteractor, it)
        createStressTracker(dataInteractor, it)
    }
}

private suspend fun createStressTracker(dataInteractor: DataInteractor, dailyGroupId: Long) {
    val stressTracker = dataInteractor.insertTracker(
        createTracker(
            name = "Stress",
            description = "Just random data",
            groupId = dailyGroupId,
            displayIndex = 8
        )
    )

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = stressTracker,
        sinTransform = SinTransform(3.0, 50.0, -1.0),
        randomOffsetScalar = 0.0,
        spacing = Duration.ofDays(1),
        spacingRandomisationHours = 4,
        numDataPoints = 50,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1),
        roundToInt = true,
        clampMin = 0.0,
        clampMax = 3.0,
        labels = listOf("None", "Low", "Medium", "High")
    )
}

private suspend fun createStudyingTracker(dataInteractor: DataInteractor, dailyGroupId: Long) {
    val studyingTracker = dataInteractor.insertTracker(
        createTracker(
            name = "Studying",
            description = "Just random data",
            groupId = dailyGroupId,
            dataType = DataType.DURATION,
            suggestionType = TrackerSuggestionType.NONE,
            displayIndex = 7
        )
    )

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = studyingTracker,
        sinTransform = SinTransform(1.0, 50.0, 6.0),
        randomOffsetScalar = 0.5,
        spacing = Duration.ofDays(1),
        spacingRandomisationHours = 4,
        numDataPoints = 1,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1)
    )
}

private suspend fun createExerciseTracker(dataInteractor: DataInteractor, dailyGroupId: Long) {
    val exerciseTracker = dataInteractor.insertTracker(
        createTracker(
            name = "Exercise",
            description = "Just random data",
            groupId = dailyGroupId,
            hasDefaultValue = true,
            suggestionType = TrackerSuggestionType.NONE,
            defaultValue = 1.0,
            displayIndex = 6
        )
    )

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = exerciseTracker,
        sinTransform = SinTransform(1.0, 50.0, 6.0),
        randomOffsetScalar = 0.5,
        numDataPoints = 1,
        spacing = Duration.ofDays(1),
        spacingRandomisationHours = 4,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1)
    )
}

private suspend fun createWeightTracker(dataInteractor: DataInteractor, dailyGroupId: Long) {
    val weightTracker = dataInteractor.insertTracker(
        createTracker(
            name = "Weight",
            description = "Just random data",
            suggestionType = TrackerSuggestionType.NONE,
            groupId = dailyGroupId,
            displayIndex = 5
        )
    )

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = weightTracker,
        sinTransform = SinTransform(1.0, 50.0, 6.0),
        randomOffsetScalar = 0.5,
        numDataPoints = 1,
        spacing = Duration.ofDays(1),
        spacingRandomisationHours = 4,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1)
    )
}

private suspend fun createWorkTracker(dataInteractor: DataInteractor, dailyGroupId: Long) {
    val workTracker = dataInteractor.insertTracker(
        createTracker(
            name = "Work",
            description = "Just random data",
            groupId = dailyGroupId,
            dataType = DataType.DURATION,
            suggestionType = TrackerSuggestionType.NONE,
            displayIndex = 4
        )
    )

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = workTracker,
        sinTransform = SinTransform(1.0, 50.0, 6.0),
        randomOffsetScalar = 0.5,
        spacing = Duration.ofDays(1),
        numDataPoints = 1,
        spacingRandomisationHours = 4,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1)
    )
}

private suspend fun createMeditationTracker(dataInteractor: DataInteractor, dailyGroupId: Long) {
    val meditationTracker = dataInteractor.insertTracker(
        createTracker(
            name = "Meditation",
            description = "Just random data",
            groupId = dailyGroupId,
            dataType = DataType.DURATION,
            suggestionType = TrackerSuggestionType.NONE,
            displayIndex = 3
        )
    )

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = meditationTracker,
        sinTransform = SinTransform(1.0, 50.0, 6.0),
        randomOffsetScalar = 0.5,
        numDataPoints = 1,
        spacing = Duration.ofDays(1),
        spacingRandomisationHours = 4,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1)
    )
}

private suspend fun createAlcoholTracker(dataInteractor: DataInteractor, dailyGroupId: Long) {
    val alcoholTracker = dataInteractor.insertTracker(
        createTracker(
            name = "Alcohol",
            groupId = dailyGroupId,
            description = "Just random data",
            suggestionType = TrackerSuggestionType.NONE,
            displayIndex = 2
        )
    )

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = alcoholTracker,
        sinTransform = SinTransform(1.0, 50.0, 6.0),
        randomOffsetScalar = 0.5,
        numDataPoints = 1,
        spacing = Duration.ofDays(1),
        spacingRandomisationHours = 4,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1)
    )
}

private suspend fun createProductivityTracker(dataInteractor: DataInteractor, dailyGroupId: Long) {
    val productivityTracker = dataInteractor.insertTracker(
        createTracker(
            name = "Productivity",
            description = "Just random data",
            groupId = dailyGroupId,
            suggestionType = TrackerSuggestionType.NONE,
            displayIndex = 1
        )
    )

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = productivityTracker,
        sinTransform = SinTransform(1.0, 50.0, 6.0),
        randomOffsetScalar = 0.5,
        numDataPoints = 1,
        spacing = Duration.ofDays(1),
        spacingRandomisationHours = 4,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1)
    )
}

private suspend fun createSleepTracker(dataInteractor: DataInteractor, dailyGroupId: Long) {
    val sleepTracker = dataInteractor.insertTracker(
        createTracker(
            name = "Sleep",
            groupId = dailyGroupId,
            dataType = DataType.DURATION,
            suggestionType = TrackerSuggestionType.NONE,
            displayIndex = 0
        )
    )

    createWaveData(
        dataInteractor = dataInteractor,
        trackerId = sleepTracker,
        sinTransform = SinTransform(SEC_PER_HOUR * 1.5, 50.0, SEC_PER_HOUR * 6.0),
        randomOffsetScalar = 0.5,
        spacingRandomisationHours = 4,
        endPoint = OffsetDateTime.now().withHour(22).minusDays(1)
    )
}
