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

package com.samco.trackandgraph.navigation

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.samco.trackandgraph.MainActivity
import com.samco.trackandgraph.base.navigation.PendingIntentProvider
import com.samco.trackandgraph.base.service.TrackWidgetProvider
import com.samco.trackandgraph.base.service.TrackWidgetProvider.Companion.UPDATE_FEATURE_ID
import com.samco.trackandgraph.base.service.TrackWidgetProvider.Companion.UPDATE_FEATURE_TIMER
import com.samco.trackandgraph.timers.AddDataPointFromTimerActivity
import com.samco.trackandgraph.widgets.TrackWidgetInputDataPointActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PendingIntentProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PendingIntentProvider {
    override fun getMainActivityPendingIntent(): PendingIntent {
        return Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .let { PendingIntent.getActivity(context, 0, it, 0) }
    }

    override fun getDurationInputActivityIntent(featureId: Long, startInstant: String): Intent {
        return Intent(context, AddDataPointFromTimerActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AddDataPointFromTimerActivity.FEATURE_ID_KEY, featureId)
            .putExtra(AddDataPointFromTimerActivity.START_TIME_KEY, startInstant)
    }


    override fun getDurationInputActivityPendingIntent(
        featureId: Long,
        startInstant: String
    ): PendingIntent {
        return getDurationInputActivityIntent(featureId, startInstant).let {
            PendingIntent.getActivity(
                context,
                //A key unique to this request to allow updating notification
                startInstant.hashCode() + featureId.toInt(),
                it,
                0
            )
        }
    }

    override fun getTrackWidgetInputDataPointActivityPendingIntent(appWidgetId: Int): PendingIntent {
        return Intent(context, TrackWidgetInputDataPointActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }.let {
            PendingIntent.getActivity(
                context,
                appWidgetId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    override fun getTrackWidgetStartStopTimerIntent(
        appWidgetId: Int,
        featureId: Long,
        startTimer: Boolean
    ): PendingIntent {
        return Intent(
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            null, context, TrackWidgetProvider::class.java
        ).apply {
            putExtra(UPDATE_FEATURE_ID, featureId)
            putExtra(UPDATE_FEATURE_TIMER, startTimer)
        }.let {
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                it,
                PendingIntent.FLAG_CANCEL_CURRENT
            )
        }
    }
}