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
package com.samco.trackandgraph.addtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.samco.trackandgraph.MainActivity
import com.samco.trackandgraph.NavButtonStyle
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.theming.FormTheme
import com.samco.trackandgraph.util.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AddTrackerFragment : Fragment() {
    private val args: AddTrackerFragmentArgs by navArgs()
    private val viewModel by viewModels<AddTrackerViewModelImpl>()
    private var navController: NavController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.init(args.groupId, args.editFeatureId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        navController = container?.findNavController()
        return ComposeView(requireContext()).apply {
            setContent {
                FormTheme {
                    AddTrackerView(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.complete.observe(viewLifecycleOwner) {
            if (it) navController?.popBackStack()
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).setActionBarConfig(
            NavButtonStyle.UP,
            getString(R.string.add_tracker)
        )
    }

    override fun onStop() {
        super.onStop()
        requireActivity().window.hideKeyboard(requireActivity().currentFocus?.windowToken)
    }
}