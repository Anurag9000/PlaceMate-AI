package com.example.placemate.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.placemate.R
import com.example.placemate.databinding.FragmentHomeBinding
import com.example.placemate.ui.inventory.InventoryAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = InventoryAdapter(
            onItemClick = { item ->
                val bundle = Bundle().apply { putString("itemId", item.id) }
                findNavController().navigate(R.id.nav_item_detail, bundle)
            },
            onFolderClick = { location ->
                // Dashboard also supports drill-down? For now, just go to Inventory
                val bundle = Bundle().apply { putString("locationId", location.id) }
                findNavController().navigate(R.id.nav_inventory, bundle)
            },
            onFolderLongClick = { _ ->
                // No specific long-click action on home for now, or could show Toast
            }
        )

        binding.rvRecentActivity.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentActivity.adapter = adapter

        // Bind UI State
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.totalItemsCount.collect { count ->
                        binding.tvTotalItemsCount.text = count.toString()
                    }
                }
                launch {
                    viewModel.takenItemsCount.collect { count ->
                        binding.tvMissingItemsCount.text = count.toString()
                    }
                }
                launch {
                    viewModel.aiEngineStatus.collect { status ->
                        binding.tvAiEngine.text = "AI Engine: $status"
                    }
                }
            }
        }

        // Deep links from dashboard
        binding.btnHomeScan.setOnClickListener {
            findNavController().navigate(R.id.nav_inventory) 
            // In a real app we might pass a flag to auto-trigger scan
        }

        binding.btnHomeAdd.setOnClickListener {
            findNavController().navigate(R.id.nav_add_item)
        }

        binding.cardOmniSearch.setOnClickListener {
            findNavController().navigate(R.id.nav_omni_search)
        }
        
        binding.cardAiStatus.setOnClickListener {
            findNavController().navigate(R.id.nav_settings)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
