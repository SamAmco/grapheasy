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

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.samco.trackandgraph.base.database.dto.GroupChildType

class GroupAdapter(
    private val trackerClickListener: TrackerClickListener,
    private val graphStatClickListener: GraphStatClickListener,
    private val groupClickListener: GroupClickListener,
) : RecyclerView.Adapter<GroupChildViewHolder>() {
    private val groupChildren = mutableListOf<GroupChild>()

    private enum class ViewType {
        GRAPH,
        TRACKER,
        GROUP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupChildViewHolder {
        return when (viewType) {
            ViewType.GRAPH.ordinal -> GraphStatViewHolder(ComposeView(parent.context))
            ViewType.TRACKER.ordinal -> TrackerViewHolder.from(parent)
            else -> GroupViewHolder.from(parent).apply { setFullSpan(this) }
        }
    }

    private fun setFullSpan(vh: RecyclerView.ViewHolder) {
        val layoutParams = StaggeredGridLayoutManager.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        layoutParams.isFullSpan = true
        vh.itemView.layoutParams = layoutParams
    }

    override fun onBindViewHolder(holder: GroupChildViewHolder, position: Int) {
        val item = groupChildren[position]
        when (getItemViewType(position)) {
            ViewType.GRAPH.ordinal -> (holder as GraphStatViewHolder).bind(
                (item as GroupChild.ChildGraph).graph.viewData,
                graphStatClickListener
            )

            ViewType.TRACKER.ordinal -> (holder as TrackerViewHolder)
                .bind((item as GroupChild.ChildTracker).displayTracker, trackerClickListener)

            ViewType.GROUP.ordinal -> (holder as GroupViewHolder)
                .bind((item as GroupChild.ChildGroup).group, groupClickListener)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val child = groupChildren.getOrNull(position) ?: return -1
        return when (child) {
            is GroupChild.ChildGraph -> ViewType.GRAPH.ordinal
            is GroupChild.ChildTracker -> ViewType.TRACKER.ordinal
            is GroupChild.ChildGroup -> ViewType.GROUP.ordinal
        }
    }

    override fun getItemCount(): Int = groupChildren.size

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newChildren: List<GroupChild>, forceNotifyDataSetChanged: Boolean) {
        if (forceNotifyDataSetChanged) {
            groupChildren.clear()
            groupChildren.addAll(newChildren)
            notifyDataSetChanged()
        } else {
            val diffCallback = ListDiffCallback(groupChildren, newChildren)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            groupChildren.clear()
            groupChildren.addAll(newChildren)
            diffResult.dispatchUpdatesTo(this)
        }
    }

    fun getItems(): List<GroupChild> = groupChildren

    fun moveItem(start: Int, end: Int) {
        if (start < 0 || start > groupChildren.size || end < 0 || end > groupChildren.size) return
        val child = groupChildren.removeAt(start)
        groupChildren.add(end, child)
        notifyItemMoved(start, end)
    }

    fun getSpanModeForItem(position: Int): SpanMode? {
        if (position < 0 || position > groupChildren.size) return null
        return when (groupChildren[position].type) {
            GroupChildType.TRACKER -> SpanMode.NumSpans(1)
            GroupChildType.GROUP -> SpanMode.NumSpans(2)
            GroupChildType.GRAPH -> SpanMode.FullWidth
        }
    }
}

sealed class SpanMode {
    object FullWidth : SpanMode()
    data class NumSpans(val spans: Int) : SpanMode()
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
        return old.type == new.type && old.id == new.id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        return when {
            old is GroupChild.ChildGroup && new is GroupChild.ChildGroup -> {
                val oldObj = old.group.copy(displayIndex = 0)
                val newObj = new.group.copy(displayIndex = 0)
                oldObj == newObj
            }

            old is GroupChild.ChildTracker && new is GroupChild.ChildTracker -> {
                val oldObj = old.displayTracker.copy(displayIndex = 0)
                val newObj = new.displayTracker.copy(displayIndex = 0)
                oldObj == newObj
            }

            old is GroupChild.ChildGraph && new is GroupChild.ChildGraph -> {
                return old.graph.time == new.graph.time
            }

            else -> false
        }
    }
}

abstract class GroupChildViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun elevateCard()
    abstract fun dropCard()

    /**
     * @brief Called each seconds, usually to refresh timers
     */
    open fun update() {}
}