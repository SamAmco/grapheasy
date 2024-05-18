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

import android.view.LayoutInflater
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.samco.trackandgraph.R
import com.samco.trackandgraph.adddatapoint.AddDataPointsDialog
import com.samco.trackandgraph.adddatapoint.AddDataPointsViewModelImpl
import com.samco.trackandgraph.base.helpers.formatDayMonthYearHourMinuteWeekDayOneLine
import com.samco.trackandgraph.base.helpers.getWeekDayNames
import com.samco.trackandgraph.ui.compose.theming.tngColors
import com.samco.trackandgraph.ui.compose.ui.DateScrollLazyColumn
import com.samco.trackandgraph.ui.compose.ui.EmptyScreenText
import com.samco.trackandgraph.ui.compose.ui.SpacingExtraSmall
import com.samco.trackandgraph.ui.showNoteDialog

@Composable
fun NotesView(
    notesViewModel: NotesViewModel,
    addDataPointsDialogViewModel: AddDataPointsViewModelImpl,
    globalNoteDialogViewModel: GlobalNoteInputViewModel
) {
    val dateScrollData = notesViewModel.dateScrollData.observeAsState().value

    val context = LocalContext.current

    if (dateScrollData == null || dateScrollData.items.isEmpty()) {
        EmptyScreenText(textId = R.string.no_data_points_history_fragment_hint)
    } else {
        DateScrollLazyColumn(
            modifier = Modifier.padding(dimensionResource(id = R.dimen.card_margin_small)),
            data = dateScrollData
        ) { note ->
            Note(
                noteInfo = note,
                onNoteClick = {
                    showNoteDialog(
                        LayoutInflater.from(context),
                        context,
                        note.toDisplayNote(),
                        note.featurePath
                    )
                },
                onEditClick = {
                    if (note.trackerId != null) {
                        addDataPointsDialogViewModel.showAddDataPointDialog(
                            trackerId = note.trackerId,
                            dataPointTimestamp = note.date
                        )
                    } else globalNoteDialogViewModel.openDialog(note.date)
                },
                onDeleteClick = { notesViewModel.deleteNote(note) }
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.card_margin_small)))
        }
    }

    AddDataPointsDialog(
        viewModel = addDataPointsDialogViewModel,
        onDismissRequest = { addDataPointsDialogViewModel.reset() }
    )

    if (globalNoteDialogViewModel.show.observeAsState(false).value) {
        GlobalNoteInputDialogView(viewModel = globalNoteDialogViewModel)
    }
}

@Composable
private fun Note(
    noteInfo: NoteInfo,
    onNoteClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) = Card(
    modifier = Modifier.clickable { onNoteClick() },
    elevation = dimensionResource(id = R.dimen.card_elevation),
    shape = MaterialTheme.shapes.small
) {

    var boxEnd by remember { mutableStateOf(0) }

    Box(modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { boxEnd = it.size.width }
    ) {
        var menuExpanded by remember { mutableStateOf(false) }

        IconButton(
            modifier = Modifier.align(Alignment.TopEnd),
            onClick = { menuExpanded = true }
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.list_menu_icon),
                contentDescription = stringResource(id = R.string.edit),
                tint = MaterialTheme.tngColors.onSurface
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            offset = DpOffset(boxEnd.dp, 0.dp),
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(onClick = {
                menuExpanded = false
                onEditClick()
            }) {
                Text(
                    text = stringResource(id = R.string.edit),
                    style = MaterialTheme.typography.body1
                )
            }

            DropdownMenuItem(onClick = {
                menuExpanded = false
                onDeleteClick()
            }) {
                Text(
                    text = stringResource(id = R.string.delete),
                    style = MaterialTheme.typography.body1
                )
            }
        }

    }

    val context = LocalContext.current
    val weekDayNames = remember(Locale.current) {
        getWeekDayNames(context)
    }

    val dateText = remember(noteInfo.date) {
        formatDayMonthYearHourMinuteWeekDayOneLine(
            context,
            weekDayNames,
            noteInfo.date
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(id = R.dimen.card_padding))
    ) {
        Text(
            style = MaterialTheme.typography.subtitle2,
            text = dateText,
        )
        SpacingExtraSmall()
        if (noteInfo.featurePath.isNotBlank()) {
            Text(
                style = MaterialTheme.typography.body1,
                text = noteInfo.featurePath,
                color = MaterialTheme.tngColors.onSurface.copy(alpha = 0.5f)
            )
            SpacingExtraSmall()
        }
        Text(
            style = MaterialTheme.typography.body1,
            text = noteInfo.note,
            maxLines = 3
        )
    }
}