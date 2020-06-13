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
package com.samco.trackandgraph.graphsandstats

import android.app.Application
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.samco.trackandgraph.R
import com.samco.trackandgraph.database.TrackAndGraphDatabase
import com.samco.trackandgraph.database.TrackAndGraphDatabaseDao
import com.samco.trackandgraph.database.GraphOrStat
import com.samco.trackandgraph.database.GraphStatType
import com.samco.trackandgraph.graphstatview.GraphStatCardView
import com.samco.trackandgraph.ui.OrderedListAdapter
import kotlinx.coroutines.*

private val getIdForGraphStat = { gs: GraphOrStat -> gs.id }

class GraphStatAdapter(
    private val clickListener: GraphStatClickListener,
    application: Application
) : OrderedListAdapter<GraphOrStat, GraphStatViewHolder>(
    getIdForGraphStat,
    GraphStatDiffCallback()
) {
    private val dataSource = TrackAndGraphDatabase.getInstance(application).trackAndGraphDatabaseDao

    override fun onBindViewHolder(holder: GraphStatViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener, dataSource)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GraphStatViewHolder {
        return GraphStatViewHolder.from(parent)
    }
}

class GraphStatViewHolder(private val graphStatCardView: GraphStatCardView) :
    RecyclerView.ViewHolder(graphStatCardView), PopupMenu.OnMenuItemClickListener {
    private var currJob: Job? = null
    private var clickListener: GraphStatClickListener? = null
    private var graphStat: GraphOrStat? = null
    private var dropElevation = 0f

    fun bind(
        graphStat: GraphOrStat,
        clickListener: GraphStatClickListener,
        dataSource: TrackAndGraphDatabaseDao
    ) {
        this.dropElevation = graphStatCardView.cardView.cardElevation
        this.graphStat = graphStat
        this.clickListener = clickListener
        currJob?.cancel()
        currJob = Job()
        graphStatCardView.menuButtonClickListener = { v -> createContextMenu(v) }
        graphStatCardView.cardView.setOnClickListener { clickListener.onClick(graphStat) }
        CoroutineScope(Dispatchers.Main + currJob!!).launch {
            val foundGraphStat = when (graphStat.type) {
                GraphStatType.LINE_GRAPH -> tryInitLineGraph(dataSource, graphStat)
                GraphStatType.PIE_CHART -> tryInitPieChart(dataSource, graphStat)
                GraphStatType.TIME_SINCE -> tryInitTimeSinceStat(dataSource, graphStat)
                GraphStatType.AVERAGE_TIME_BETWEEN -> tryInitAverageTimeBetween(
                    dataSource,
                    graphStat
                )
            }
            if (!foundGraphStat) graphStatCardView.graphStatView.initError(
                graphStat,
                R.string.graph_stat_view_not_found
            )
        }
    }

    fun elevateCard() {
        graphStatCardView.cardView.postDelayed({
            graphStatCardView.cardView.cardElevation = graphStatCardView.cardView.cardElevation * 3f
        }, 10)
    }

    fun dropCard() {
        graphStatCardView.cardView.cardElevation = dropElevation
    }

    private fun createContextMenu(view: View) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.edit_graph_stat_context_menu, popup.menu)
        popup.setOnMenuItemClickListener(this)
        popup.show()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        graphStat?.let {
            when (item?.itemId) {
                R.id.edit -> clickListener?.onEdit(it)
                R.id.delete -> clickListener?.onDelete(it)
                R.id.moveTo -> clickListener?.onMoveGraphStat(it)
                R.id.duplicate -> clickListener?.onDuplicate(it)
                else -> {
                }
            }
        }
        return false
    }

    private suspend fun tryInitPieChart(
        dataSource: TrackAndGraphDatabaseDao,
        graphStat: GraphOrStat
    ): Boolean {
        val pieChart = withContext(Dispatchers.IO) {
            dataSource.getPieChartByGraphStatId(graphStat.id)
        } ?: return false
        graphStatCardView.graphStatView.initFromPieChart(graphStat, pieChart)
        return true
    }

    private suspend fun tryInitAverageTimeBetween(
        dataSource: TrackAndGraphDatabaseDao,
        graphStat: GraphOrStat
    ): Boolean {
        val avTimeStat = withContext(Dispatchers.IO) {
            dataSource.getAverageTimeBetweenStatByGraphStatId(graphStat.id)
        } ?: return false
        graphStatCardView.graphStatView.initAverageTimeBetweenStat(graphStat, avTimeStat)
        return true
    }

    private suspend fun tryInitTimeSinceStat(
        dataSource: TrackAndGraphDatabaseDao,
        graphStat: GraphOrStat
    ): Boolean {
        val timeSinceStat = withContext(Dispatchers.IO) {
            dataSource.getTimeSinceLastStatByGraphStatId(graphStat.id)
        } ?: return false
        graphStatCardView.graphStatView.initTimeSinceStat(graphStat, timeSinceStat)
        return true
    }

    private suspend fun tryInitLineGraph(
        dataSource: TrackAndGraphDatabaseDao,
        graphStat: GraphOrStat
    ): Boolean {
        val lineGraph = withContext(Dispatchers.IO) {
            dataSource.getLineGraphByGraphStatId(graphStat.id)
        } ?: return false
        graphStatCardView.graphStatView.initFromLineGraph(graphStat, lineGraph, true)
        return true
    }

    companion object {
        fun from(parent: ViewGroup): GraphStatViewHolder {
            val graphStat = GraphStatCardView(parent.context)
            graphStat.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            return GraphStatViewHolder(graphStat)
        }
    }
}

class GraphStatDiffCallback : DiffUtil.ItemCallback<GraphOrStat>() {
    override fun areItemsTheSame(oldItem: GraphOrStat, newItem: GraphOrStat) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: GraphOrStat, newItem: GraphOrStat) = oldItem == newItem
}

class GraphStatClickListener(
    private val onDelete: (graphStat: GraphOrStat) -> Unit,
    private val onEdit: (graphStat: GraphOrStat) -> Unit,
    private val onClick: (graphStat: GraphOrStat) -> Unit,
    private val onMoveGraphStat: (graphStat: GraphOrStat) -> Unit,
    private val onDuplicateGraphStat: (graphStat: GraphOrStat) -> Unit
) {
    fun onDelete(graphStat: GraphOrStat) = onDelete.invoke(graphStat)
    fun onEdit(graphStat: GraphOrStat) = onEdit.invoke(graphStat)
    fun onClick(graphStat: GraphOrStat) = onClick.invoke(graphStat)
    fun onMoveGraphStat(graphStat: GraphOrStat) = onMoveGraphStat.invoke(graphStat)
    fun onDuplicate(graphStat: GraphOrStat) = onDuplicateGraphStat.invoke(graphStat)
}