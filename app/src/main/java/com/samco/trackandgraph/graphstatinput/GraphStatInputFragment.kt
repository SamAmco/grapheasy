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
package com.samco.trackandgraph.graphstatinput

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.samco.trackandgraph.MainActivity
import com.samco.trackandgraph.NavButtonStyle
import com.samco.trackandgraph.R
import com.samco.trackandgraph.settings.TngSettings
import com.samco.trackandgraph.ui.compose.compositionlocals.LocalSettings
import com.samco.trackandgraph.ui.compose.theming.FormTheme
import com.samco.trackandgraph.util.hideKeyboard
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GraphStatInputFragment : Fragment() {
    private var navController: NavController? = null
    private val args: GraphStatInputFragmentArgs by navArgs()
    private val viewModel: GraphStatInputViewModel by viewModels<GraphStatInputViewModelImpl>()

    @Inject
    lateinit var tngSettings: TngSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initViewModel(args.groupId, args.graphStatId)
    }

    override fun onStart() {
        super.onStart()
        viewModel.complete.observe(viewLifecycleOwner, Observer {
            if (it) navController?.popBackStack()
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        navController = container?.findNavController()
        return ComposeView(requireContext()).apply {
            setContent {
                CompositionLocalProvider(LocalSettings provides tngSettings) {
                    FormTheme {
                        GraphStatInputView(
                            viewModelStoreOwner = this@GraphStatInputFragment,
                            viewModel = viewModel,
                            graphStatId = args.graphStatId
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).setActionBarConfig(
            NavButtonStyle.UP,
            getString(R.string.add_a_graph_or_stat)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().window.hideKeyboard(requireActivity().currentFocus?.windowToken)
    }
}

