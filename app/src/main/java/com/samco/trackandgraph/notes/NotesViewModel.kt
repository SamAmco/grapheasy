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
package com.samco.trackandgraph.notes

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.samco.trackandgraph.base.database.dto.DisplayNote
import com.samco.trackandgraph.base.database.dto.GlobalNote
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.ui.compose.ui.Datable
import com.samco.trackandgraph.ui.compose.ui.DateDisplayResolution
import com.samco.trackandgraph.ui.compose.ui.DateScrollData
import com.samco.trackandgraph.util.FeaturePathProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import javax.inject.Inject

data class NoteInfo(
    override val date: OffsetDateTime,
    val trackerId: Long?,
    val featureId: Long?,
    val featureName: String?,
    val featurePath: String,
    val groupId: Long?,
    val note: String
) : Datable {
    fun toDisplayNote(): DisplayNote = DisplayNote(
        timestamp = date,
        trackerId = trackerId,
        featureId = featureId,
        featureName = featureName,
        groupId = groupId,
        note = note
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val dataInteractor: DataInteractor,
    @IODispatcher private val io: CoroutineDispatcher
) : ViewModel() {
    private val notesFlow = dataInteractor.getAllDisplayNotes()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val featureNameProvider = flow {
        val trackers = dataInteractor.getAllTrackersSync()
        val groups = dataInteractor.getAllGroupsSync()
        emit(FeaturePathProvider(trackers, groups))
    }.flowOn(io)

    val dateScrollData: LiveData<DateScrollData<NoteInfo>> = notesFlow
        .filter { it.isNotEmpty() }
        .combine(featureNameProvider) { list, featurePathProvider ->
            val range = Duration
                .between(list.last().timestamp, list.first().timestamp)
                .abs()

            val dateDisplayResolution = when {
                range.toDays() > 365 -> DateDisplayResolution.MONTH_YEAR
                else -> DateDisplayResolution.MONTH_DAY
            }

            return@combine DateScrollData(
                items = list.map { it.asNoteInfo(featurePathProvider) },
                dateDisplayResolution = dateDisplayResolution
            )
        }
        .asLiveData(viewModelScope.coroutineContext)

    //Emits true and then false any time the top note in the list is changed so that the
    // view knows to scroll back to the top of the list
    val onNoteInsertedTop: LiveData<Boolean> =
        notesFlow.scan(Pair<DisplayNote?, DisplayNote?>(null, null)) { acc, value ->
            //Every time there's a new list of notes get the top note and save it along with
            // the top note from the last list
            return@scan Pair(acc.second, value.firstOrNull())
        }.flatMapLatest {
            //If the top note from the last list is not the same as the top note from this list
            // emit true/false to scroll to the top
            return@flatMapLatest if (it.first != it.second) flow {
                emit(true)
                emit(false)
            } else emptyFlow()
        }.asLiveData(viewModelScope.coroutineContext)


    fun deleteNote(note: NoteInfo) = viewModelScope.launch(io) {
        note.trackerId?.let {
            dataInteractor.removeNote(note.date, it)
        } ?: run {
            dataInteractor.deleteGlobalNote(GlobalNote(note.date, note.note))
        }
    }

    private fun DisplayNote.asNoteInfo(
        featurePathProvider: FeaturePathProvider,
    ) = NoteInfo(
        date = timestamp,
        trackerId = trackerId,
        featureId = featureId,
        featureName = featureName,
        featurePath = featureId
            ?.let { featurePathProvider.getPathForFeature(it) }
            ?: featureName
            ?: "",
        groupId = groupId,
        note = note
    )
}