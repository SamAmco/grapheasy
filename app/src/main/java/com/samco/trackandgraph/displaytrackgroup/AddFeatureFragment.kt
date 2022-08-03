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
package com.samco.trackandgraph.displaytrackgroup

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.samco.trackandgraph.databinding.AddFeatureFragmentBinding
import com.samco.trackandgraph.databinding.FeatureDiscreteValueListItemBinding
import kotlinx.coroutines.*
import androidx.core.view.children
import androidx.core.view.forEachIndexed
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.samco.trackandgraph.MainActivity
import com.samco.trackandgraph.NavButtonStyle
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.DiscreteValue
import com.samco.trackandgraph.base.database.dto.DataType
import com.samco.trackandgraph.base.database.dto.Feature
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.model.di.MainDispatcher
import com.samco.trackandgraph.ui.YesCancelDialogFragment
import com.samco.trackandgraph.util.getColorFromAttr
import com.samco.trackandgraph.util.getDoubleFromText
import com.samco.trackandgraph.util.hideKeyboard
import com.samco.trackandgraph.util.focusAndShowKeyboard
import com.samco.trackandgraph.widgets.TrackWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.lang.Exception
import javax.inject.Inject
import kotlin.math.absoluteValue
import com.samco.trackandgraph.base.model.FeatureUpdater.DurationNumericConversionMode as DurationNumericConversionMode

@AndroidEntryPoint
class AddFeatureFragment : Fragment(), YesCancelDialogFragment.YesCancelDialogListener {
    private val args: AddFeatureFragmentArgs by navArgs()
    private lateinit var binding: AddFeatureFragmentBinding
    private val viewModel by viewModels<AddFeatureViewModel>()
    private var navController: NavController? = null
    private val featureTypeList = listOf(
        DataType.DISCRETE,
        DataType.CONTINUOUS,
        DataType.DURATION,
        DataType.TIMESTAMP
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        navController = container?.findNavController()
        binding = AddFeatureFragmentBinding.inflate(inflater, container, false)

        viewModel.init(
            args.groupId,
            args.existingFeatureNames.toList(),
            args.editFeatureId
        )

        listenToViewModelState()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).setActionBarConfig(
            NavButtonStyle.UP,
            getString(R.string.add_feature)
        )
    }

    override fun onStop() {
        super.onStop()
        requireActivity().window.hideKeyboard(requireActivity().currentFocus?.windowToken)
    }

    private fun listenToViewModelState() {
        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                AddFeatureState.INITIALIZING -> {
                    setInitialViewState()
                }
                AddFeatureState.SET_FOCUS -> {
                    binding.featureNameText.focusAndShowKeyboard()
                }
                AddFeatureState.WAITING -> {
                    onViewModelReady()
                    binding.progressBar.visibility = View.GONE
                }
                AddFeatureState.ADDING -> {
                    binding.addBar.addButton.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }
                AddFeatureState.DONE -> {
                    updateAnyExistingWidgets()
                    navController?.popBackStack()
                }
                AddFeatureState.ERROR -> {
                    val errorMsg = getString(R.string.feature_add_or_update_error_occurred)
                    Toast.makeText(requireActivity(), errorMsg, Toast.LENGTH_LONG).show()
                    navController?.popBackStack()
                }
                else -> {
                }
            }
        })
    }

    private fun setInitialViewState() {
        binding.discreteValuesTextView.visibility = View.INVISIBLE
        binding.discreteValues.visibility = View.INVISIBLE
        binding.addDiscreteValueButton.visibility = View.INVISIBLE
        binding.defaultNumericalInput.visibility = View.INVISIBLE
        binding.defaultDurationInput.visibility = View.INVISIBLE
        binding.defaultDiscreteScrollView.visibility = View.INVISIBLE
        binding.durationNumericConversionModeSpinner.visibility = View.INVISIBLE
        binding.durationToNumericModeHeader.visibility = View.INVISIBLE
        binding.numericToDurationModeHeader.visibility = View.INVISIBLE
    }

    private fun updateAnyExistingWidgets() {
        val intent = Intent(
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            null, context, TrackWidgetProvider::class.java
        )
        intent.putExtra(
            com.samco.trackandgraph.widgets.UPDATE_FEATURE_ID,
            args.editFeatureId
        )
        activity?.sendBroadcast(intent)
    }

    private fun onViewModelReady() {
        initViewFromViewModel()
        observeHasDefaultValue()
        observeFeatureType()
    }

    private fun getOnFeatureSelectedListener(): AdapterView.OnItemSelectedListener {
        return object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {}

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                viewModel.featureType.value = featureTypeList[position]
            }
        }
    }

    private fun initViewFromViewModel() {
        viewModel.discreteValues.forEach { v -> inflateDiscreteValue(v) }
        if (viewModel.updateMode) initSpinnerInUpdateMode()

        binding.featureDescriptionText.setText(viewModel.featureDescription)
        binding.featureTypeSpinner.setSelection(featureTypeList.indexOf(viewModel.featureType.value!!))
        binding.featureTypeSpinner.onItemSelectedListener = getOnFeatureSelectedListener()

        binding.featureNameText.setText(viewModel.featureName)
        binding.featureNameText.setSelection(binding.featureNameText.text.length)
        binding.featureNameText.requestFocus()
        binding.featureNameText.addTextChangedListener {
            viewModel.featureName = binding.featureNameText.text.toString()
            validateForm()
        }
        binding.featureDescriptionText.addTextChangedListener {
            viewModel.featureDescription = binding.featureDescriptionText.text.toString()
        }
        binding.addDiscreteValueButton.setOnClickListener { onAddDiscreteValue() }
        binding.addBar.addButton.setOnClickListener { onAddOrUpdateClicked() }
        binding.hasDefaultValueCheckbox.setOnCheckedChangeListener { _, checked ->
            viewModel.featureHasDefaultValue.value = checked
        }
        binding.hasDefaultValueCheckbox.isChecked = viewModel.featureHasDefaultValue.value!!
        binding.defaultNumericalInput.addTextChangedListener {
            viewModel.featureDefaultValue.value = getDoubleFromText(it.toString())
        }
        binding.defaultDurationInput.setDurationChangedListener {
            viewModel.featureDefaultValue.value = it.toDouble()
        }
        binding.defaultDurationInput.setDoneListener {
            requireActivity().window.hideKeyboard()
        }
        binding.durationNumericConversionModeSpinner.onItemSelectedListener =
            getOnDurationNumericConversionModeSelectedListener()
    }

    private fun getOnDurationNumericConversionModeSelectedListener(): AdapterView.OnItemSelectedListener {
        return object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {}

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                viewModel.durationNumericConversionMode.value =
                    DurationNumericConversionMode.values()[position]
            }
        }
    }

    private fun observeFeatureType() {
        viewModel.featureType.observe(viewLifecycleOwner) {
            onFeatureTypeChanged(it)
        }
    }

    private fun observeHasDefaultValue() {
        viewModel.featureHasDefaultValue.observe(viewLifecycleOwner) {
            updateDefaultValuesViewFromViewModel(it)
        }
    }

    private fun updateDefaultValuesViewFromViewModel(checked: Boolean) {
        if (checked) {
            when (viewModel.featureType.value) {
                DataType.DISCRETE -> {
                    if (currentDefaultValueIsInvalidDiscreteValue()) {
                        viewModel.featureDefaultValue.value = 0.0
                    }
                    updateCurrentlySelectedDefaultDiscreteValue()
                    binding.defaultDiscreteScrollView.visibility = View.VISIBLE
                    binding.defaultNumericalInput.visibility = View.GONE
                    binding.defaultDurationInput.visibility = View.GONE
                }
                DataType.CONTINUOUS -> {
                    binding.defaultNumericalInput.setText(viewModel.featureDefaultValue.value!!.toString())
                    binding.defaultDiscreteScrollView.visibility = View.GONE
                    binding.defaultNumericalInput.visibility = View.VISIBLE
                    binding.defaultDurationInput.visibility = View.GONE
                }
                DataType.DURATION -> {
                    binding.defaultDurationInput.setTimeInSeconds(viewModel.featureDefaultValue.value!!.toLong())
                    binding.defaultDiscreteScrollView.visibility = View.GONE
                    binding.defaultNumericalInput.visibility = View.GONE
                    binding.defaultDurationInput.visibility = View.VISIBLE
                }
                DataType.TIMESTAMP -> {
                    binding.defaultDiscreteScrollView.visibility = View.GONE
                    binding.defaultNumericalInput.visibility = View.GONE
                    binding.defaultDurationInput.visibility = View.GONE
                }
                null -> {}
            }
        } else {
            binding.defaultDiscreteScrollView.visibility = View.GONE
            binding.defaultNumericalInput.visibility = View.GONE
            binding.defaultDurationInput.visibility = View.GONE
        }
    }

    private fun currentDefaultValueIsInvalidDiscreteValue(): Boolean {
        val currentDefaultValue = viewModel.featureDefaultValue.value!!
        return currentDefaultValue > viewModel.discreteValues.size - 1
                || currentDefaultValue < 0
                || (currentDefaultValue - currentDefaultValue.toInt().toDouble()).absoluteValue > 0
    }

    private fun initSpinnerInUpdateMode() {
        val itemList = resources.getStringArray(R.array.feature_types)
            .map { s -> s as CharSequence }.toTypedArray()
        binding.featureTypeSpinner.adapter = object : ArrayAdapter<CharSequence>(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item, itemList
        ) {
            override fun isEnabled(position: Int): Boolean {
                return viewModel.isFeatureTypeEnabled(featureTypeList[position])
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view as TextView
                val enabled = viewModel.isFeatureTypeEnabled(featureTypeList[position])
                val color =
                    if (enabled) view.context.getColorFromAttr(android.R.attr.textColorPrimary)
                    else view.context.getColorFromAttr(android.R.attr.textColorHint)
                textView.setTextColor(color)
                return view
            }
        }
    }

    private fun onAddOrUpdateClicked() {
        if (!viewModel.updateMode) viewModel.onAddOrUpdate()
        else {
            val dialog = YesCancelDialogFragment()
            val args = Bundle()
            args.putString("title", getString(R.string.ru_sure_update_feature))
            dialog.arguments = args
            childFragmentManager.let { dialog.show(it, "ru_sure_update_feature_fragment") }
        }
    }

    private fun inflateDiscreteValue(label: AddFeatureViewModel.MutableLabel) {
        inlateDiscreteValueInputCard(label)
        inflateDiscreteValueDefaultButton(label)
        reIndexDiscreteValueViews()
        validateForm()
    }

    private fun updateCurrentlySelectedDefaultDiscreteValue() {
        val selected = viewModel.featureDefaultValue.value!!.toInt()
        binding.defaultDiscreteButtonsLayout.children.forEachIndexed { i, view ->
            (view as CheckBox).isChecked = i == selected
        }
    }

    private fun inflateDiscreteValueDefaultButton(label: AddFeatureViewModel.MutableLabel) {
        val layout = binding.defaultDiscreteButtonsLayout
        val item =
            layoutInflater.inflate(R.layout.discrete_value_input_button, layout, false) as CheckBox
        item.text = label.label
        val index = viewModel.discreteValues.indexOf(label)
        item.isChecked =
            viewModel.featureHasDefaultValue.value!! && index == viewModel.featureDefaultValue.value?.toInt()
        item.setOnClickListener {
            viewModel.featureDefaultValue.value = viewModel.discreteValues.indexOf(label).toDouble()
            updateCurrentlySelectedDefaultDiscreteValue()
        }
        layout.addView(item, index)
    }

    private fun updateDefaultValueButtonsText() {
        binding.defaultDiscreteButtonsLayout.children.forEachIndexed { i, view ->
            (view as CheckBox).text = viewModel.discreteValues[i].label
        }
    }

    private fun inlateDiscreteValueInputCard(label: AddFeatureViewModel.MutableLabel) {
        val item = FeatureDiscreteValueListItemBinding.inflate(
            layoutInflater,
            binding.discreteValues,
            false
        )
        val inputText = item.discreteValueNameText
        inputText.addTextChangedListener {
            label.label = it.toString().trim()
            updateDefaultValueButtonsText()
            validateForm()
        }
        inputText.setText(label.label)
        item.deleteButton.setOnClickListener {
            onDeleteDiscreteValue(item)
            validateForm()
        }
        item.upButton.setOnClickListener {
            onUpClickedDiscreteValue(item)
            validateForm()
        }
        item.downButton.setOnClickListener {
            onDownClickedDiscreteValue(item)
            validateForm()
        }
        binding.discreteValues.addView(item.root)
        binding.scrollView.post {
            binding.scrollView.fullScroll(View.FOCUS_DOWN)
            inputText.requestFocus()
        }
    }

    private fun onAddDiscreteValue() {
        val label = AddFeatureViewModel.MutableLabel()
        viewModel.discreteValues.add(label)
        inflateDiscreteValue(label)
    }

    private fun validateForm() {
        var errorSet = false
        val discreteValueStrings = viewModel.discreteValues
        if (viewModel.featureType.value!! == DataType.DISCRETE) {
            if (discreteValueStrings.isEmpty() || discreteValueStrings.size < 2) {
                setErrorText(getString(R.string.discrete_feature_needs_at_least_two_values))
                errorSet = true
            }
            for (s in discreteValueStrings) {
                if (s.label.isEmpty()) {
                    setErrorText(getString(R.string.discrete_value_must_have_name))
                    errorSet = true
                }
            }
        }
        binding.featureNameText.text.let {
            if (it.isEmpty()) {
                setErrorText(getString(R.string.feature_name_cannot_be_null))
                errorSet = true
            } else if (viewModel.existingFeatureNames.contains(it.toString())) {
                setErrorText(getString(R.string.feature_with_that_name_exists))
                errorSet = true
            }
        }
        if (errorSet) {
            binding.addBar.addButton.isEnabled = false
        } else {
            binding.addBar.addButton.isEnabled = true
            setErrorText("")
        }
    }

    private fun setErrorText(text: String) {
        binding.addBar.errorText.text = text
    }

    private fun onDownClickedDiscreteValue(item: FeatureDiscreteValueListItemBinding) {
        val currIndex = binding.discreteValues.indexOfChild(item.root)
        if (currIndex == binding.discreteValues.childCount - 1) return
        viewModel.discreteValues.add(currIndex + 1, viewModel.discreteValues.removeAt(currIndex))
        binding.discreteValues.removeView(item.root)
        binding.discreteValues.addView(item.root, currIndex + 1)
        val defaultButtonView = binding.defaultDiscreteButtonsLayout.getChildAt(currIndex)
        binding.defaultDiscreteButtonsLayout.removeViewAt(currIndex)
        binding.defaultDiscreteButtonsLayout.addView(defaultButtonView, currIndex + 1)
        if (currIndex == viewModel.featureDefaultValue.value!!.toInt())
            viewModel.featureDefaultValue.value = viewModel.featureDefaultValue.value!! + 1
        reIndexDiscreteValueViews()
    }

    private fun onUpClickedDiscreteValue(item: FeatureDiscreteValueListItemBinding) {
        val currIndex = binding.discreteValues.indexOfChild(item.root)
        if (currIndex == 0) return
        viewModel.discreteValues.add(currIndex - 1, viewModel.discreteValues.removeAt(currIndex))
        binding.discreteValues.removeView(item.root)
        binding.discreteValues.addView(item.root, currIndex - 1)
        val defaultButtonView = binding.defaultDiscreteButtonsLayout.getChildAt(currIndex)
        binding.defaultDiscreteButtonsLayout.removeViewAt(currIndex)
        binding.defaultDiscreteButtonsLayout.addView(defaultButtonView, currIndex - 1)
        if (currIndex == viewModel.featureDefaultValue.value!!.toInt())
            viewModel.featureDefaultValue.value = viewModel.featureDefaultValue.value!! - 1
        reIndexDiscreteValueViews()
    }

    private fun onDeleteDiscreteValue(item: FeatureDiscreteValueListItemBinding) {
        if (viewModel.updateMode && !viewModel.haveWarnedAboutDeletingDiscreteValues) {
            AlertDialog.Builder(context)
                .setTitle(R.string.warning)
                .setMessage(R.string.on_feature_delete_discrete_value_warning)
                .setPositiveButton(R.string.ok, null)
                .show()
            viewModel.haveWarnedAboutDeletingDiscreteValues = true
        }
        val currIndex = binding.discreteValues.indexOfChild(item.root)
        viewModel.discreteValues.removeAt(currIndex)
        binding.discreteValues.removeView(item.root)
        binding.addDiscreteValueButton.isEnabled = true
        binding.defaultDiscreteButtonsLayout.removeViewAt(currIndex)
        if (currIndex == viewModel.featureDefaultValue.value!!.toInt())
            viewModel.featureDefaultValue.value = 0.0
        reIndexDiscreteValueViews()
    }

    private fun reIndexDiscreteValueViews() {
        binding.discreteValues.forEachIndexed { i, v ->
            v.findViewById<TextView>(R.id.indexText).text = "$i : "
        }
    }

    private fun updateDurationNumericConversionUI() {
        val durationToNumeric = viewModel.updateMode
                && viewModel.existingFeature?.featureType == DataType.DURATION
                && viewModel.featureType.value == DataType.CONTINUOUS
        val numericToDuration = viewModel.updateMode
                && viewModel.existingFeature?.featureType == DataType.CONTINUOUS
                && viewModel.featureType.value == DataType.DURATION
        binding.durationToNumericModeHeader.visibility =
            if (durationToNumeric) View.VISIBLE else View.GONE
        binding.numericToDurationModeHeader.visibility =
            if (numericToDuration) View.VISIBLE else View.GONE
        binding.durationNumericConversionModeSpinner.visibility =
            if (durationToNumeric || numericToDuration) View.VISIBLE else View.GONE
    }

    private fun onFeatureTypeChanged(featureType: DataType) {
        showOnFeatureTypeUpdatedMessage(featureType)
        updateDurationNumericConversionUI()
        updateDefaultValuesViewFromViewModel(viewModel.featureHasDefaultValue.value!!)
        val vis = if (featureType == DataType.DISCRETE) View.VISIBLE else View.GONE
        binding.discreteValuesTextView.visibility = vis
        binding.discreteValues.visibility = vis
        binding.addDiscreteValueButton.visibility = vis
        validateForm()
    }

    private fun showOnFeatureTypeUpdatedMessage(newType: DataType) {
        val oldType = viewModel.existingFeature?.featureType
        if (viewModel.updateMode && oldType != null && oldType != newType) {
            val message = getOnDataTypeChangedMessage(oldType, newType)

            message?.let {
                AlertDialog.Builder(context)
                    .setTitle(R.string.warning)
                    .setMessage(it)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }

    private fun getOnDataTypeChangedMessage(oldType: DataType, newType: DataType): String? {
        return when (oldType) {
            DataType.DISCRETE -> when (newType) {
                DataType.DISCRETE -> null
                DataType.CONTINUOUS -> getString(R.string.on_feature_type_change_numerical_warning)
                else -> null
            }
            else -> null
        }
    }

    override fun onDialogYes(dialog: YesCancelDialogFragment, id: String?) {
        when (dialog.title) {
            getString(R.string.ru_sure_update_feature) -> viewModel.onAddOrUpdate()
        }
    }
}

enum class AddFeatureState { INITIALIZING, SET_FOCUS, WAITING, ADDING, DONE, ERROR }

@HiltViewModel
class AddFeatureViewModel @Inject constructor(
    private val dataInteractor: DataInteractor,
    @IODispatcher private val io: CoroutineDispatcher,
    @MainDispatcher private val ui: CoroutineDispatcher
) : ViewModel() {

    class MutableLabel(var label: String = "", val updateIndex: Int = -1)

    var featureName = ""
    var featureDescription = ""
    val featureType = MutableLiveData(DataType.DISCRETE)
    val durationNumericConversionMode = MutableLiveData(DurationNumericConversionMode.HOURS)
    val featureHasDefaultValue = MutableLiveData(false)
    val featureDefaultValue = MutableLiveData(1.0)
    val discreteValues = mutableListOf<MutableLabel>()
    lateinit var existingFeatureNames: List<String?>
        private set

    val state: LiveData<AddFeatureState>
        get() {
            return _state
        }
    private val _state = MutableLiveData(AddFeatureState.INITIALIZING)

    var updateMode: Boolean = false
        private set
    var haveWarnedAboutDeletingDiscreteValues = false
    private var trackGroupId: Long = -1
    var existingFeature: Feature? = null
        private set
    private var initialized = false

    fun init(trackGroupId: Long, existingFeatureNames: List<String>, existingFeatureId: Long) {
        if (initialized) return
        initialized = true

        this.trackGroupId = trackGroupId
        this.existingFeatureNames = existingFeatureNames
        viewModelScope.launch(io) {
            if (existingFeatureId > -1) {
                updateMode = true
                existingFeature = dataInteractor.getFeatureById(existingFeatureId)
                val existingDiscreteValues = existingFeature!!.discreteValues
                    .sortedBy { f -> f.index }
                    .map { f -> MutableLabel(f.label, f.index) }
                withContext(ui) {
                    featureName = existingFeature!!.name
                    featureDescription = existingFeature!!.description
                    featureType.value = existingFeature!!.featureType
                    featureHasDefaultValue.value = existingFeature!!.hasDefaultValue
                    featureDefaultValue.value = existingFeature!!.defaultValue
                    this@AddFeatureViewModel.existingFeatureNames =
                        existingFeatureNames.minus(featureName)
                    discreteValues.addAll(existingDiscreteValues)
                }
            }
            withContext(ui) {
                _state.value = AddFeatureState.SET_FOCUS
                _state.value = AddFeatureState.WAITING
            }
        }
    }

    fun isFeatureTypeEnabled(type: DataType): Boolean {
        if (!updateMode) return true
        // disc -> cont Y
        // disc -> dur N
        // cont -> disc N
        // cont -> dur Y
        // dur -> disc N
        // dur -> cont Y
        return when (type) {
            DataType.DISCRETE -> existingFeature!!.featureType == DataType.DISCRETE
            DataType.CONTINUOUS -> existingFeature!!.featureType == DataType.DURATION
                    || existingFeature!!.featureType == DataType.DISCRETE
                    || existingFeature!!.featureType == DataType.CONTINUOUS
            DataType.DURATION -> existingFeature!!.featureType == DataType.CONTINUOUS
                    || existingFeature!!.featureType == DataType.DURATION
            DataType.TIMESTAMP -> existingFeature!!.featureType == DataType.CONTINUOUS
                    || existingFeature!!.featureType == DataType.DURATION
        }
    }

    fun onAddOrUpdate() {
        _state.value = AddFeatureState.ADDING
        viewModelScope.launch(io) {
            try {
                if (updateMode) updateFeature()
                else addFeature()
                withContext(ui) { _state.value = AddFeatureState.DONE }
            } catch (e: Exception) {
                withContext(ui) { _state.value = AddFeatureState.ERROR }
            }
        }
    }

    private suspend fun updateFeature() {
        val existingDiscreteValues = existingFeature!!.discreteValues

        val newDiscVals = discreteValues.associateWith { mutableLabel ->
            val index = if (discreteValues.size == 1) 1 else discreteValues.indexOf(mutableLabel)
            DiscreteValue(index, mutableLabel.label)
        }

        val discValMap = existingDiscreteValues
            .filter { dv -> discreteValues.map { it.updateIndex }.contains(dv.index) }
            .associateWith {
                newDiscVals.getOrElse(discreteValues.first { new -> new.updateIndex == it.index }) {
                    throw Exception("Could not find discrete value update")
                }
            }

        dataInteractor.updateFeature(
            existingFeature!!,
            discreteValueMap = discValMap,
            durationNumericConversionMode = durationNumericConversionMode.value,
            newName = featureName,
            newFeatureType = featureType.value,
            newDiscreteValues = newDiscVals.values.toList(),
            hasDefaultValue = featureHasDefaultValue.value,
            defaultValue = featureDefaultValue.value,
            featureDescription = featureDescription
        )
    }

    private suspend fun addFeature() {
        val discVals = discreteValues.mapIndexed { i, s ->
            val index = if (discreteValues.size == 1) 1 else i
            DiscreteValue(index, s.label)
        }
        val feature = Feature(
            0,
            featureName, trackGroupId,
            featureType.value!!, discVals, 0,
            featureHasDefaultValue.value!!, featureDefaultValue.value!!,
            featureDescription
        )
        dataInteractor.insertFeature(feature)
    }
}
