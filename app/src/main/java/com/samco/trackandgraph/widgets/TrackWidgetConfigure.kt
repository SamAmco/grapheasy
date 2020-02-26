package com.samco.trackandgraph.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import com.samco.trackandgraph.database.FeatureAndTrackGroup
import com.samco.trackandgraph.database.TrackAndGraphDatabase
import com.samco.trackandgraph.database.TrackAndGraphDatabaseDao
import com.samco.trackandgraph.databinding.TrackWidgetConfigureBinding

class TrackWidgetConfigure : FragmentActivity() {

    private var appWidgetId: Int? = null
    private lateinit var viewModel: TrackWidgetConfigureViewModel
    private lateinit var binding: TrackWidgetConfigureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = TrackWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViewModel()

        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED)
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this).get(TrackWidgetConfigureViewModel::class.java)
        viewModel.initViewModel(this)
        viewModel.allFeatures.observe(this, Observer { features ->
            if (features.isEmpty()) {
                Toast.makeText(applicationContext, "Create a data set first!", Toast.LENGTH_SHORT).show()
                finish()
            }
            val itemNames = features.map {ft -> "${ft.trackGroupName} -> ${ft.name}"}
            val adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, itemNames)
            binding.featureSpinner.adapter = adapter
            binding.featureSpinner.setSelection(0)
            binding.featureSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
        })
    }

    fun onConfirm(view: View) {
        appWidgetId?.let { id ->
            val featureId = viewModel.featureId
            if (featureId == null) {
                Toast.makeText(applicationContext, "Select a data set", Toast.LENGTH_SHORT).show()
                return
            }

            val sharedPref = getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE).edit()
            sharedPref.putLong(TrackWidgetProvider.getFeatureIdPref(id), featureId)
            sharedPref.apply()

            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, TrackWidgetProvider::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
            sendBroadcast(intent)

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

class TrackWidgetConfigureViewModel : ViewModel() {
    private var dataSource: TrackAndGraphDatabaseDao? = null
    lateinit var allFeatures: LiveData<List<FeatureAndTrackGroup>> private set
    var featureId: Long? = null

    fun initViewModel(activity: Activity) {
        if (dataSource != null) return
        dataSource = TrackAndGraphDatabase.getInstance(activity.application).trackAndGraphDatabaseDao
        allFeatures = dataSource!!.getAllFeaturesAndTrackGroups()
    }
}