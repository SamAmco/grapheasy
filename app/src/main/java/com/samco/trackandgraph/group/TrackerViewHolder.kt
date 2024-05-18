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
import android.view.*
import android.widget.PopupMenu
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.DataType
import com.samco.trackandgraph.base.database.dto.DisplayTracker
import com.samco.trackandgraph.base.helpers.formatRelativeTimeSpan
import com.samco.trackandgraph.base.helpers.formatTimeDuration
import com.samco.trackandgraph.databinding.ListItemTrackerBinding
import org.threeten.bp.Duration
import org.threeten.bp.Instant

class TrackerViewHolder private constructor(
    private val binding: ListItemTrackerBinding,
) : GroupChildViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {
    private var clickListener: TrackerClickListener? = null
    private var tracker: DisplayTracker? = null
    private var dropElevation = 0f

    private val context = binding.root.context

    fun bind(tracker: DisplayTracker, clickListener: TrackerClickListener) {
        this.tracker = tracker
        this.clickListener = clickListener
        this.dropElevation = binding.cardView.cardElevation
        setLastDateText()
        binding.trackerNameText.text = tracker.name
        binding.menuButton.setOnClickListener { createContextMenu(binding.menuButton) }
        initClickEvents(tracker, clickListener)
        initTimerControls(tracker, clickListener)
    }

    private fun initTimerControls(tracker: DisplayTracker, clickListener: TrackerClickListener) {
        if (tracker.dataType != DataType.DURATION) {
            binding.playTimerButton.visibility = View.GONE
            binding.stopTimerButton.visibility = View.GONE
            binding.timerText.visibility = View.GONE
            binding.lastDateText.visibility = View.VISIBLE
        } else if (tracker.timerStartInstant != null) {
            binding.timerText.visibility = View.VISIBLE
            binding.playTimerButton.visibility = View.GONE
            binding.stopTimerButton.visibility = View.VISIBLE
            binding.lastDateText.visibility = View.GONE
            updateTimerText()
        } else {
            binding.timerText.visibility = View.GONE
            binding.playTimerButton.visibility = View.VISIBLE
            binding.stopTimerButton.visibility = View.GONE
            binding.lastDateText.visibility = View.VISIBLE
            binding.timerText.text = formatTimeDuration(0)
        }

        binding.playTimerButton.setOnClickListener {
            clickListener.onPlayTimer(tracker)
        }
        binding.stopTimerButton.setOnClickListener {
            clickListener.onStopTimer(tracker)
        }
    }

    override fun update() {
        super.update()
        updateTimerText()
        setLastDateText()
    }

    private fun updateTimerText() {
        tracker?.timerStartInstant?.let {
            val duration = Duration.between(it, Instant.now())
            binding.timerText.text = formatTimeDuration(duration.seconds)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initClickEvents(tracker: DisplayTracker, clickListener: TrackerClickListener) {
        var lastX = 0
        var lastY = 0
        val isInCorner = {
            val rect = binding.innerLayoutContainer

            //45dp in px
            val cornerSize = 45 * context.resources.displayMetrics.density

            lastX > (rect.width - cornerSize)
                    && lastX < rect.width
                    && lastY > (rect.height - cornerSize)
                    && lastY < rect.height
        }
        binding.cardView.setOnLongClickListener {
            if (isInCorner()) {
                clickListener.onAdd(tracker, false)
                return@setOnLongClickListener true
            }
            return@setOnLongClickListener false
        }
        binding.cardView.setOnClickListener {
            if (isInCorner()) {
                clickListener.onAdd(tracker)
            } else {
                clickListener.onHistory(tracker)
            }
        }
        binding.innerLayoutContainer.setOnTouchListener { _, event ->
            //ignore this listener but update lastX and lastY
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                lastX = event.x.toInt()
                lastY = event.y.toInt()
            }
            return@setOnTouchListener false
        }
        if (tracker.hasDefaultValue) {
            binding.addButton.visibility = View.INVISIBLE
            binding.quickAddButton.visibility = View.VISIBLE
        } else {
            binding.addButton.visibility = View.VISIBLE
            binding.quickAddButton.visibility = View.INVISIBLE
        }
    }

    private fun setLastDateText() {
        val timestamp = tracker?.timestamp
        binding.lastDateText.text = if (timestamp == null) {
            context.getString(R.string.no_data)
        } else {
            formatRelativeTimeSpan(context, timestamp)
        }
    }

    override fun elevateCard() {
        binding.cardView.postDelayed({ binding.cardView.cardElevation *= 3f }, 10)
    }

    override fun dropCard() {
        binding.cardView.cardElevation = dropElevation
    }

    private fun createContextMenu(view: View) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.edit_tracker_context_menu, popup.menu)
        popup.setOnMenuItemClickListener(this)
        popup.show()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        tracker?.let {
            when (item?.itemId) {
                R.id.edit -> clickListener?.onEdit(it)
                R.id.delete -> clickListener?.onDelete(it)
                R.id.moveTo -> clickListener?.onMoveTo(it)
                R.id.description -> clickListener?.onDescription(it)
                else -> {
                }
            }
        }
        return false
    }

    companion object {
        fun from(parent: ViewGroup): TrackerViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = ListItemTrackerBinding.inflate(layoutInflater, parent, false)
            return TrackerViewHolder(binding)
        }
    }
}

class TrackerClickListener(
    private val onEditListener: (tracker: DisplayTracker) -> Unit,
    private val onDeleteListener: (tracker: DisplayTracker) -> Unit,
    private val onMoveToListener: (tracker: DisplayTracker) -> Unit,
    private val onDescriptionListener: (tracker: DisplayTracker) -> Unit,
    private val onAddListener: (tracker: DisplayTracker, useDefault: Boolean) -> Unit,
    private val onHistoryListener: (tracker: DisplayTracker) -> Unit,
    private val onPlayTimerListener: (tracker: DisplayTracker) -> Unit,
    private val onStopTimerListener: (tracker: DisplayTracker) -> Unit,
) {
    fun onEdit(tracker: DisplayTracker) = onEditListener(tracker)
    fun onDelete(tracker: DisplayTracker) = onDeleteListener(tracker)
    fun onMoveTo(tracker: DisplayTracker) = onMoveToListener(tracker)
    fun onDescription(tracker: DisplayTracker) = onDescriptionListener(tracker)
    fun onAdd(tracker: DisplayTracker, useDefault: Boolean = true) =
        onAddListener(tracker, useDefault)

    fun onHistory(tracker: DisplayTracker) = onHistoryListener(tracker)
    fun onPlayTimer(tracker: DisplayTracker) = onPlayTimerListener(tracker)
    fun onStopTimer(tracker: DisplayTracker) = onStopTimerListener(tracker)
}
