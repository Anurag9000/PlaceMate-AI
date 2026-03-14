package com.example.placemate.ui.taken

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
import com.example.placemate.core.input.SpeechManager
import com.example.placemate.core.input.SpeechState
import com.example.placemate.databinding.FragmentTakenItemsBinding
import com.example.placemate.ui.inventory.InventoryAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TakenItemsFragment : Fragment() {

    private var _binding: FragmentTakenItemsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TakenItemsViewModel by viewModels()

    @Inject
    lateinit var speechManager: SpeechManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTakenItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = InventoryAdapter(
            onItemClick = { item ->
                val bundle = Bundle().apply { putString("itemId", item.id) }
                findNavController().navigate(R.id.nav_item_detail, bundle)
            },
            onFolderClick = { },
            onFolderLongClick = { }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnSpeechTaken.setOnClickListener { startSpeechSearch() }
        binding.cardOmniSearch.setOnClickListener {
            findNavController().navigate(R.id.nav_omni_search)
        }
        binding.searchEditText.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.takenItems.collect { items ->
                    adapter.submitList(items)
                }
            }
        }
    }

    private fun startSpeechSearch() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                speechManager.startListening().collect { state ->
                    if (state is SpeechState.Result) {
                        binding.searchEditText.setText(state.text)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
