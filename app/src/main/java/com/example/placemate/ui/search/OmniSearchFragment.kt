package com.example.placemate.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.placemate.R
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.core.utils.ImageUtils
import com.example.placemate.databinding.FragmentOmniSearchBinding
import com.example.placemate.ui.inventory.InventoryAdapter
import com.example.placemate.ui.inventory.InventoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class OmniSearchFragment : Fragment() {

    private var _binding: FragmentOmniSearchBinding? = null
    private val binding get() = _binding!!

    // We can reuse InventoryViewModel for search logic or create a dedicated one
    private val viewModel: InventoryViewModel by viewModels()

    @javax.inject.Inject
    lateinit var speechManager: com.example.placemate.core.input.SpeechManager

    @javax.inject.Inject
    lateinit var recognitionService: ItemRecognitionService

    private var photoFile: File? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) launchCamera()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) launchSpeechRecognition()
    }

    private val takePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoFile?.let { file ->
                val uri = android.net.Uri.fromFile(file)
                binding.progressBar.visibility = View.VISIBLE
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = recognitionService.recognizeItem(uri)
                    binding.progressBar.visibility = View.GONE
                    result.suggestedName?.let { name ->
                        binding.etSearch.setText(name)
                        viewModel.updateSearchQuery(name)
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOmniSearchBinding.inflate(inflater, container, false)
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
                val bundle = Bundle().apply { putString("locationId", location.id) }
                findNavController().navigate(R.id.nav_inventory, bundle)
            },
            onFolderLongClick = { _ -> }
        )

        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = adapter

        binding.etSearch.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }

        binding.btnVoiceSearch.setOnClickListener { startVoiceSearch() }
        binding.btnCameraSearch.setOnClickListener { startCameraSearch() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.explorerItems.collect { items ->
                adapter.submitList(items)
                binding.tvEmptyState.visibility = if (items.isEmpty() && binding.etSearch.text.isNotEmpty()) View.VISIBLE else if (binding.etSearch.text.isEmpty()) View.VISIBLE else View.GONE
                binding.tvEmptyState.text = if (binding.etSearch.text.isEmpty()) "Try searching for something..." else "No matches found."
            }
        }
    }

    private fun startCameraSearch() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        photoFile = ImageUtils.createImageFile(requireContext())
        val uri = ImageUtils.getContentUri(requireContext(), photoFile!!)
        takePictureLauncher.launch(uri)
    }

    private fun startVoiceSearch() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognition()
        } else {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchSpeechRecognition() {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            speechManager.startListening().collect { state ->
                if (state is com.example.placemate.core.input.SpeechState.Result) {
                    binding.progressBar.visibility = View.GONE
                    binding.etSearch.setText(state.text)
                    viewModel.updateSearchQuery(state.text)
                } else if (state is com.example.placemate.core.input.SpeechState.Error) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
