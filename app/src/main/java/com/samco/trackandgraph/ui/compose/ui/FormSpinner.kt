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
package com.samco.trackandgraph.ui.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

@Composable
fun <T> FormSpinner(
    modifier: Modifier = Modifier,
    strings: Map<T, String>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    // Once selected from the list, an item can be displayed differently.
    selectedItemTransform: (T) -> String? = { currentElement: T ->
        strings[currentElement]
    },
    enabled: Boolean = true
) {
    var expanded: Boolean by remember { mutableStateOf(false) }

    FormInputDecoration(
        isFocused = false,
        modifier = modifier
            .let {
                if(enabled) {
                    it.clickable {
                        expanded = !expanded
                    }
                }
                else it
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = selectedItemTransform(selectedItem) ?: "",
                fontSize = MaterialTheme.typography.body1.fontSize,
                fontWeight = MaterialTheme.typography.body1.fontWeight,
            )

            val angle = if (expanded) 180F else 0.0F
            Icon(Icons.Rounded.ArrowDropDown,null, modifier = Modifier.rotate(angle))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(color = MaterialTheme.colors.surface)
        ) {
            val items = strings.keys.toList()
            items.forEachIndexed { index, element ->
                DropdownMenuItem(
                    enabled = enabled,
                    onClick = {
                        onItemSelected(items[index])
                        expanded = false
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = strings[element] ?: "",
                            fontSize = MaterialTheme.typography.body1.fontSize,
                            fontWeight = MaterialTheme.typography.body1.fontWeight
                        )
                    }
                }
            }
        }
    }
}
