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
package com.samco.trackandgraph.addgroup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samco.trackandgraph.base.database.dto.Group
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.ui.dataVisColorList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

interface AddGroupDialogViewModel {
    val colorIndex: Int
    val name: TextFieldValue
    val hidden: Boolean
    val loading: Boolean

    val updateMode: StateFlow<Boolean>
    val addEnabled: StateFlow<Boolean>

    fun show(parentGroupId: Long?, groupId: Long?)
    fun addOrUpdateGroup()
    fun hide()
    fun updateColorIndex(index: Int)
    fun updateName(name: TextFieldValue)
}

@HiltViewModel
class AddGroupDialogViewModelImpl @Inject constructor(
    private val dataInteractor: DataInteractor
) : ViewModel(), AddGroupDialogViewModel {
    override var colorIndex: Int by mutableStateOf(0)
        private set

    override var name: TextFieldValue by mutableStateOf(TextFieldValue())
        private set

    override var hidden: Boolean by mutableStateOf(true)
        private set

    override var loading: Boolean by mutableStateOf(false)
        private set

    private var parentGroupId: Long? = null
    private var currentGroup = MutableStateFlow<Group?>(null)

    override val addEnabled: StateFlow<Boolean> = combine(
        snapshotFlow { colorIndex },
        snapshotFlow { name }
    ) { colorIndex, name ->
        colorIndex in dataVisColorList.indices && name.text.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    override val updateMode: StateFlow<Boolean> = currentGroup
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    override fun show(parentGroupId: Long?, groupId: Long?) {
        colorIndex = 0
        name = TextFieldValue()
        this.parentGroupId = parentGroupId
        loading = false
        hidden = false
        if (groupId != null) {
            viewModelScope.launch {
                loading = true
                val group = dataInteractor.getGroupById(groupId)
                this@AddGroupDialogViewModelImpl.currentGroup.value = group
                colorIndex = group.colorIndex
                name = TextFieldValue(group.name, TextRange(group.name.length))
                loading = false
            }
        } else {
            this.currentGroup.value = null
        }
    }

    override fun addOrUpdateGroup() {
        if (colorIndex !in dataVisColorList.indices || name.text.isBlank()) return
        val name = name.text
        val colorIndex = colorIndex
        viewModelScope.launch {
            currentGroup.value?.let { current ->
                dataInteractor.updateGroup(
                    Group(
                        id = current.id,
                        name = name,
                        displayIndex = current.displayIndex,
                        parentGroupId = current.parentGroupId,
                        colorIndex = colorIndex
                    )
                )
            } ?: run {
                dataInteractor.insertGroup(
                    Group(
                        id = 0,
                        name = name,
                        displayIndex = 0,
                        parentGroupId = parentGroupId ?: 0L,
                        colorIndex = colorIndex
                    )
                )
            }
            hide()
        }
    }

    override fun hide() {
        hidden = true
    }

    override fun updateColorIndex(index: Int) {
        colorIndex = index
    }

    override fun updateName(name: TextFieldValue) {
        this.name = name
    }
}