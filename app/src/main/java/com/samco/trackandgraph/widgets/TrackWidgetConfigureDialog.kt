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

package com.samco.trackandgraph.widgets

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.databinding.TrackWidgetConfigureDialogBinding
import com.samco.trackandgraph.ui.FeaturePathProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class TrackWidgetConfigureDialog : DialogFragment() {
    private val viewModel by viewModels<TrackWidgetConfigureDialogViewModel>()
    private lateinit var binding: TrackWidgetConfigureDialogBinding
    private lateinit var listener: TrackWidgetConfigureDialogListener

    internal interface TrackWidgetConfigureDialogListener {
        fun onCreateWidget(featureId: Long?)
        fun onNoFeatures()
        fun onDismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return activity?.let {
            binding = TrackWidgetConfigureDialogBinding.inflate(inflater, container, false)
            listener = activity as TrackWidgetConfigureDialogListener

            binding.cancelButton.setOnClickListener {
                dismiss()
                listener.onDismiss()
            }
            binding.createButton.setOnClickListener { listener.onCreateWidget(viewModel.featureId) }
            observeAllFeatures()

            dialog?.setCanceledOnTouchOutside(true)
            binding.root
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun observeAllFeatures() = viewModel.featurePathProvider
        .observe(this) { featurePathProvider ->
            val features = featurePathProvider.features
            if (features.isEmpty()) {
                listener.onNoFeatures()
            }
            val itemNames = features.map { ft -> featurePathProvider.getPathForFeature(ft.id) }
            val adapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    itemNames
                )
            binding.featureSpinner.adapter = adapter
            binding.featureSpinner.setSelection(0)
            binding.featureSpinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        viewModel.featureId = features[position].id
                    }
                }
        }

    override fun onDismiss(dialog: DialogInterface) {
        listener.onDismiss()
    }
}

@HiltViewModel
class TrackWidgetConfigureDialogViewModel @Inject constructor(
    dataInteractor: DataInteractor
) : ViewModel() {
    val featurePathProvider: LiveData<FeaturePathProvider>
    var featureId: Long? = null

    init {
        val mediator = MediatorLiveData<FeaturePathProvider>()
        dataInteractor.let {
            val groups = it.getAllGroups()
            val features = it.getAllFeatures()
            val onEmitted = {
                val featureList = features.value
                val groupList = groups.value
                if (groupList != null && featureList != null) {
                    mediator.value = FeaturePathProvider(featureList, groupList)
                }
            }
            mediator.addSource(groups) { onEmitted() }
            mediator.addSource(features) { onEmitted() }
        }
        featurePathProvider = mediator
    }
}
