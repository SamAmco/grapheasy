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

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import com.samco.trackandgraph.MainActivity
import com.samco.trackandgraph.NavButtonStyle
import com.samco.trackandgraph.R
import com.samco.trackandgraph.adddatapoint.AddDataPointsDialog
import com.samco.trackandgraph.adddatapoint.AddDataPointsViewModelImpl
import com.samco.trackandgraph.addgroup.AddGroupDialog
import com.samco.trackandgraph.addgroup.AddGroupDialogViewModelImpl
import com.samco.trackandgraph.base.database.dto.*
import com.samco.trackandgraph.databinding.FragmentGroupBinding
import com.samco.trackandgraph.addtracker.*
import com.samco.trackandgraph.graphstatview.factories.viewdto.IGraphStatViewData
import com.samco.trackandgraph.permissions.PermissionRequesterUseCase
import com.samco.trackandgraph.permissions.PermissionRequesterUseCaseImpl
import com.samco.trackandgraph.settings.TngSettings
import com.samco.trackandgraph.ui.*
import com.samco.trackandgraph.ui.compose.compositionlocals.LocalSettings
import com.samco.trackandgraph.util.bindingForViewLifecycle
import com.samco.trackandgraph.util.performTrackVibrate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The group fragment is used on the home page and in any nested group to display the contents of
 * that group. It must display a list of groups, graphs and features contained within its group.
 * The default value for args.groupId is 0L representing the root group or home page. The
 * args.groupName may be null or empty.
 */
@AndroidEntryPoint
class GroupFragment : Fragment(),
    YesCancelDialogFragment.YesCancelDialogListener,
    PermissionRequesterUseCase by PermissionRequesterUseCaseImpl() {
    private var navController: NavController? = null
    private val args: GroupFragmentArgs by navArgs()

    private var binding: FragmentGroupBinding by bindingForViewLifecycle()

    private lateinit var adapter: GroupAdapter
    private val viewModel by viewModels<GroupViewModel>()

    private val addDataPointsDialogViewModel by viewModels<AddDataPointsViewModelImpl>()
    private val addGroupDialogViewModel by viewModels<AddGroupDialogViewModelImpl>()

    private var forceNextNotifyDataSetChanged: Boolean = false

    @Inject
    lateinit var tngSettings: TngSettings

    init {
        initNotificationsPermissionRequester(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGroupBinding.inflate(inflater, container, false)

        binding.composeView.setContent {
            CompositionLocalProvider(LocalSettings provides tngSettings) {
                AddDataPointsDialog(
                    addDataPointsDialogViewModel,
                    onDismissRequest = { addDataPointsDialogViewModel.reset() }
                )

                AddGroupDialog(
                    viewModel = addGroupDialogViewModel,
                    onDismissRequest = { addGroupDialogViewModel.hide() }
                )
            }
        }

        this.navController = container?.findNavController()
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel.setGroup(args.groupId)

        binding.emptyGroupText.visibility = View.INVISIBLE

        initializeGridLayout()
        adapter = GroupAdapter(
            createTrackerClickListener(),
            createGraphStatClickListener(),
            createGroupClickListener()
        )
        binding.itemList.adapter = adapter
        //binding.itemList.itemAnimator = null
        addItemTouchHelper()
        scrollToTopOnItemAdded()

        binding.queueAddAllButton.hide()
        binding.queueAddAllButton.setOnClickListener { onQueueAddAllClicked() }
        registerForContextMenu(binding.itemList)

        listenToViewModel()
        launchUpdateChildrenLoop()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(
            GroupMenuProvider(),
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    private inner class GroupMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.group_menu, menu)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.add_tracker -> onAddTrackerClicked()
                R.id.add_graph_stat -> onAddGraphStatClicked()
                R.id.add_group -> onAddGroupClicked()
                R.id.export_button -> onExportClicked()
                R.id.import_button -> onImportClicked()
                //R.id.add_function -> onAddFunctionClicked()
                else -> return false
            }
            return true
        }
    }

    private fun onAddFunctionClicked() {
        navigate(GroupFragmentDirections.actionAddFunction(args.groupId))
    }

    /**
     * Calls an update function on all children of the recycler view once a second. This is
     * because graphs/statistics and trackers can have timers in the view holder that need to
     * be updated every second and it could be too costly to emit an entire new set of data to
     * the adapter every second for diffing.
     */
    private fun launchUpdateChildrenLoop() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(1000)
                for (i in 0..(binding.itemList.adapter?.itemCount ?: 0)) {
                    (binding.itemList.findViewHolderForAdapterPosition(i) as GroupChildViewHolder?)?.update()
                }
            }
        }
    }

    private fun addItemTouchHelper() {
        ItemTouchHelper(DragTouchHelperCallback(
            { start: Int, end: Int -> adapter.moveItem(start, end) },
            { viewModel.adjustDisplayIndexes(adapter.getItems()) }
        )).attachToRecyclerView(binding.itemList)
    }

    private fun scrollToTopOnItemAdded() {
        adapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    super.onItemRangeInserted(positionStart, itemCount)
                    //Scroll to the top when we've added something new to our group,
                    // but not when the adapter is being re-populated, e.g. when returning
                    // to this fragment from a nested group
                    if (itemCount == 1) binding.itemList.smoothScrollToPosition(0)
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        val activity = (requireActivity() as MainActivity)
        args.groupName?.let { activity.setActionBarConfig(NavButtonStyle.UP, it) }
            ?: run { activity.setActionBarConfig(NavButtonStyle.MENU) }
    }

    private fun createGroupClickListener() = GroupClickListener(
        this::onGroupSelected,
        this::onEditGroupClicked,
        this::onDeleteGroupClicked,
        this::onMoveGroupClicked
    )

    private fun onMoveGroupClicked(group: Group) {
        val dialog = MoveToDialogFragment()
        val args = Bundle()
        args.putString(MOVE_DIALOG_TYPE_KEY, MOVE_DIALOG_TYPE_GROUP)
        args.putLong(MOVE_DIALOG_GROUP_KEY, group.id)
        dialog.arguments = args
        childFragmentManager.let { dialog.show(it, "move_dialog") }
    }

    private fun onDeleteGroupClicked(group: Group) {
        val dialog = YesCancelDialogFragment()
        val args = Bundle()
        args.putString("title", getString(R.string.ru_sure_del_group))
        args.putString("id", group.id.toString())
        dialog.arguments = args
        childFragmentManager.let { dialog.show(it, "ru_sure_del_group_fragment") }
    }

    private fun onEditGroupClicked(group: Group) {
        addGroupDialogViewModel.show(
            parentGroupId = group.parentGroupId,
            groupId = group.id
        )
    }

    private fun onGroupSelected(group: Group) {
        navigate(GroupFragmentDirections.actionSelectGroup(group.id, group.name))
    }

    private fun createGraphStatClickListener() = GraphStatClickListener(
        this::onDeleteGraphStatClicked,
        this::onEditGraphStat,
        this::onGraphStatClicked,
        this::onMoveGraphStatClicked,
        viewModel::duplicateGraphOrStat
    )

    private fun onDeleteGraphStatClicked(graphOrStat: IGraphStatViewData) {
        val dialog = YesCancelDialogFragment()
        val args = Bundle()
        args.putString("title", getString(R.string.ru_sure_del_graph))
        args.putString("id", graphOrStat.graphOrStat.id.toString())
        dialog.arguments = args
        childFragmentManager.let { dialog.show(it, "ru_sure_del_group_fragment") }
    }

    private fun onMoveGraphStatClicked(graphOrStat: IGraphStatViewData) {
        val dialog = MoveToDialogFragment()
        val args = Bundle()
        args.putString(MOVE_DIALOG_TYPE_KEY, MOVE_DIALOG_TYPE_GRAPH)
        args.putLong(MOVE_DIALOG_GROUP_KEY, graphOrStat.graphOrStat.id)
        dialog.arguments = args
        childFragmentManager.let { dialog.show(it, "move_dialog") }
    }

    private fun onGraphStatClicked(graphOrStat: IGraphStatViewData) {
        navigate(GroupFragmentDirections.actionViewGraphStat(graphOrStat.graphOrStat.id))
    }

    private fun onEditGraphStat(graphOrStat: IGraphStatViewData) {
        navigate(
            GroupFragmentDirections.actionGraphStatInput(
                graphStatId = graphOrStat.graphOrStat.id,
                groupId = args.groupId
            )
        )
    }

    private fun createTrackerClickListener() = TrackerClickListener(
        this::onTrackerEditClicked,
        this::onTrackerDeleteClicked,
        this::onTrackerMoveClicked,
        this::onTrackerDescriptionClicked,
        this::onTrackerAddClicked,
        this::onTrackerHistoryClicked,
        this::onTrackerPlayTimerClicked,
        this::onStopTimerClicked
    )

    private fun onTrackerPlayTimerClicked(tracker: DisplayTracker) {
        viewModel.playTimer(tracker)
        requestNotificationPermission(requireContext())
    }

    private fun onStopTimerClicked(tracker: DisplayTracker) {
        //Due to a bug with the GridLayoutManager when you stop a timer and the timer text disappears
        // the views heights are not properly re-calculated and we need to call notifyDataSetChanged
        // to get the view heights right again
        forceNextNotifyDataSetChanged = true
        viewModel.stopTimer(tracker)
    }

    private fun onTrackerHistoryClicked(tracker: DisplayTracker) {
        navigate(GroupFragmentDirections.actionFeatureHistory(tracker.featureId, tracker.name))
    }

    private fun navigate(direction: NavDirections) {
        //This check fixes a bug where calling navigate twice quickly would cause the app to crash
        // https://stackoverflow.com/a/53737537
        if (navController?.currentDestination?.id?.equals(R.id.groupFragment) != true) return
        navController?.navigate(direction)
    }

    private fun onTrackerAddClicked(tracker: DisplayTracker, useDefault: Boolean = true) {
        if (tracker.hasDefaultValue && useDefault) {
            requireContext().performTrackVibrate()
            viewModel.addDefaultTrackerValue(tracker)
        } else addDataPointsDialogViewModel.showAddDataPointDialog(trackerId = tracker.id)
    }

    private fun onTrackerDescriptionClicked(tracker: DisplayTracker) {
        showFeatureDescriptionDialog(requireContext(), tracker.name, tracker.description)
    }

    private fun onTrackerMoveClicked(tracker: DisplayTracker) {
        val dialog = MoveToDialogFragment()
        val args = Bundle()
        args.putString(MOVE_DIALOG_TYPE_KEY, MOVE_DIALOG_TYPE_TRACK)
        args.putLong(MOVE_DIALOG_GROUP_KEY, tracker.id)
        dialog.arguments = args
        childFragmentManager.let { dialog.show(it, "move_dialog") }
    }

    private fun onTrackerEditClicked(tracker: DisplayTracker) {
        navigate(GroupFragmentDirections.actionAddTracker(args.groupId, tracker.id))
    }

    private fun onQueueAddAllClicked() {
        viewModel.trackers.let { trackers ->
            addDataPointsDialogViewModel.showAddDataPointsDialog(trackerIds = trackers.map { it.id })
        }
    }

    private fun initializeGridLayout() {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels / dm.density
        val itemSize = (screenWidth / 2f).coerceAtMost(180f)
        val spanCount = (screenWidth / itemSize).coerceAtLeast(2f).toInt()
        val gridLayoutManager = GridLayoutManager(context, spanCount)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (val spanMode = adapter.getSpanModeForItem(position)) {
                    SpanMode.FullWidth -> spanCount
                    is SpanMode.NumSpans -> spanMode.spans
                    null -> 1
                }
            }
        }
        binding.itemList.layoutManager = gridLayoutManager
    }

    private fun listenToViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) {
            binding.loadingOverlay.visibility = if (it) View.VISIBLE else View.GONE
        }
        viewModel.hasTrackers.observe(viewLifecycleOwner) {}
        viewModel.showEmptyGroupText.observe(viewLifecycleOwner) {
            binding.emptyGroupText.visibility = if (it) View.VISIBLE else View.INVISIBLE
        }
        viewModel.allChildren.observe(viewLifecycleOwner) {
            adapter.submitList(it, forceNextNotifyDataSetChanged)
            forceNextNotifyDataSetChanged = false
            updateShowQueueTrackButton()
        }
        viewModel.showDurationInputDialog.observe(viewLifecycleOwner) {
            if (it == null) return@observe

            addDataPointsDialogViewModel.showAddDataPointDialog(
                trackerId = it.trackerId,
                customInitialValue = it.duration.seconds.toDouble()
            )
            viewModel.onConsumedShowDurationInputDialog()
        }
    }

    private val queueAddAllButtonShowHideListener by lazy {
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.queueAddAllButton.hide()
                else binding.queueAddAllButton.show()
            }
        }
    }

    private fun updateShowQueueTrackButton() {
        if (viewModel.trackers.isNotEmpty()) {
            binding.queueAddAllButton.show()
            binding.itemList.removeOnScrollListener(queueAddAllButtonShowHideListener)
            binding.itemList.addOnScrollListener(queueAddAllButtonShowHideListener)
        } else {
            binding.itemList.removeOnScrollListener(queueAddAllButtonShowHideListener)
            binding.queueAddAllButton.hide()
        }
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as MainActivity).toolbar.overflowIcon =
            ContextCompat.getDrawable(requireContext(), R.drawable.add_icon)
    }

    override fun onStop() {
        super.onStop()
        if (navController?.currentDestination?.id != R.id.groupFragment) {
            (requireActivity() as MainActivity).toolbar.overflowIcon =
                ContextCompat.getDrawable(requireContext(), R.drawable.list_menu_icon)
        }
    }

    private fun onExportClicked() {
        val dialog = ExportFeaturesDialog()
        val argBundle = Bundle()
        argBundle.putLong(GROUP_ID_KEY, args.groupId)
        argBundle.putString(GROUP_NAME_KEY, args.groupName)
        dialog.arguments = argBundle
        childFragmentManager.let { dialog.show(it, "export_features_dialog") }
    }

    private fun onImportClicked() {
        val dialog = ImportFeaturesDialog()
        val argBundle = Bundle()
        argBundle.putLong(GROUP_ID_KEY, args.groupId)
        argBundle.putString(GROUP_NAME_KEY, args.groupName)
        dialog.arguments = argBundle
        childFragmentManager.let { dialog.show(it, "import_features_dialog") }
    }

    private fun onAddTrackerClicked() {
        navigate(GroupFragmentDirections.actionAddTracker(args.groupId))
    }

    private fun onAddGraphStatClicked() {
        if (viewModel.hasTrackers.value != true) {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.no_trackers_graph_stats_hint)
                .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            navigate(GroupFragmentDirections.actionGraphStatInput(groupId = args.groupId))
        }
    }

    private fun onAddGroupClicked() {
        addGroupDialogViewModel.show(
            parentGroupId = args.groupId,
            groupId = null
        )
    }

    private fun onTrackerDeleteClicked(tracker: DisplayTracker) {
        val dialog = YesCancelDialogFragment()
        val args = Bundle()
        args.putString("title", getString(R.string.ru_sure_del_feature))
        args.putString("id", tracker.featureId.toString())
        dialog.arguments = args
        childFragmentManager.let { dialog.show(it, "ru_sure_del_feature_fragment") }
    }

    override fun onDialogYes(dialog: YesCancelDialogFragment, id: String?) {
        when (dialog.title) {
            getString(R.string.ru_sure_del_feature) -> id?.let { viewModel.onDeleteFeature(it.toLong()) }
            getString(R.string.ru_sure_del_group) -> id?.let { viewModel.onDeleteGroup(it.toLong()) }
            getString(R.string.ru_sure_del_graph) -> id?.let { viewModel.onDeleteGraphStat(it.toLong()) }
        }
    }
}
