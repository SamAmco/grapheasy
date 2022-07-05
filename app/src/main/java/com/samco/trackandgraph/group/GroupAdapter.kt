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

package com.samco.trackandgraph.group

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.samco.trackandgraph.base.database.dto.DisplayFeature
import com.samco.trackandgraph.base.database.dto.Group
import com.samco.trackandgraph.graphstatproviders.GraphStatInteractorProvider
import com.samco.trackandgraph.graphstatview.factories.viewdto.IGraphStatViewData
import org.threeten.bp.Instant

class GroupAdapter(
    private val featureClickListener: FeatureClickListener,
    private val graphStatClickListener: GraphStatClickListener,
    private val groupClickListener: GroupClickListener,
    private val gsiProvider: GraphStatInteractorProvider
) : RecyclerView.Adapter<GroupChildViewHolder>() {
    private val groupChildren = mutableListOf<GroupChild>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupChildViewHolder {
        return when (viewType) {
            GroupChildType.GRAPH.ordinal -> GraphStatViewHolder.from(parent, gsiProvider)
            GroupChildType.FEATURE.ordinal -> FeatureViewHolder.from(parent)
            else -> GroupViewHolder.from(parent)
        }
    }

    override fun onBindViewHolder(holder: GroupChildViewHolder, position: Int) {
        val item = groupChildren[position]
        when (item.type) {
            GroupChildType.GRAPH -> (holder as GraphStatViewHolder)
                .bind(extractGraphViewData(item.obj), graphStatClickListener)
            GroupChildType.FEATURE -> (holder as FeatureViewHolder)
                .bind(item.obj as DisplayFeature, featureClickListener)
            GroupChildType.GROUP -> (holder as GroupViewHolder)
                .bind(item.obj as Group, groupClickListener)
        }
    }

    private fun extractGraphViewData(obj: Any): IGraphStatViewData =
        (obj as Pair<*, *>).second as IGraphStatViewData

    override fun getItemViewType(position: Int): Int {
        return groupChildren.getOrNull(position)?.type?.ordinal ?: -1
    }

    override fun getItemCount(): Int = groupChildren.size

    fun submitList(newChildren: List<GroupChild>) {
        val diffCallback = ListDiffCallback(groupChildren, newChildren)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        groupChildren.clear()
        groupChildren.addAll(newChildren)
        diffResult.dispatchUpdatesTo(this)
    }

    fun getItems(): List<GroupChild> = groupChildren

    fun moveItem(start: Int, end: Int) {
        if (start < 0 || start > groupChildren.size || end < 0 || end > groupChildren.size) return
        val child = groupChildren.removeAt(start)
        groupChildren.add(end, child)
        notifyItemMoved(start, end)
    }

    fun getSpanSizeAtPosition(position: Int): Int {
        if (position < 0 || position > groupChildren.size) return 0
        return when (groupChildren[position].type) {
            GroupChildType.FEATURE -> 1
            else -> 2
        }
    }
}

private class ListDiffCallback(
    private val oldList: List<GroupChild>,
    private val newList: List<GroupChild>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        return old.type == new.type && old.id() == new.id()
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        return when (old.type) {
            GroupChildType.GROUP -> {
                val oldObj = (old.obj as Group).copy(displayIndex = 0)
                val newObj =(new.obj as Group).copy(displayIndex = 0)
                oldObj == newObj
            }
            GroupChildType.FEATURE -> {
                val oldObj = (old.obj as DisplayFeature).copy(displayIndex = 0)
                val newObj = (new.obj as DisplayFeature).copy( displayIndex = 0 )
                oldObj == newObj
            }
            GroupChildType.GRAPH -> {
                val oldPair = old.obj as Pair<*, *>
                val newPair = new.obj as Pair<*, *>
                return (oldPair.first as Instant) == (newPair.first as Instant)
            }
        }
    }
}

abstract class GroupChildViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun elevateCard()
    abstract fun dropCard()
}