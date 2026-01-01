package com.example.placemate.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.placemate.R
import com.example.placemate.databinding.FragmentInventoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventoryViewModel by viewModels()

    @javax.inject.Inject
    lateinit var speechManager: com.example.placemate.core.input.SpeechManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = InventoryAdapter { item ->
            val bundle = Bundle().apply { putString("itemId", item.id) }
            findNavController().navigate(R.id.nav_item_detail, bundle)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.searchEditText.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.nav_add_item)
        }

        binding.btnSpeechSearch.setOnClickListener {
            startSpeechSearch()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { items ->
                    adapter.submitList(items)
                }
            }
        }
    }

    private fun startSpeechSearch() {
        viewLifecycleOwner.lifecycleScope.launch {
            speechManager.startListening().collect { state ->
                if (state is com.example.placemate.core.input.SpeechState.Result) {
                    binding.searchEditText.setText(state.text)
                    viewModel.updateSearchQuery(state.text)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
