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
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.core.utils.ImageUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventoryViewModel by viewModels()

    @javax.inject.Inject
    lateinit var speechManager: com.example.placemate.core.input.SpeechManager

    @javax.inject.Inject
    lateinit var recognitionService: ItemRecognitionService

    @javax.inject.Inject
    lateinit var synonymManager: com.example.placemate.core.utils.SynonymManager

    private var photoFile: File? = null


    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchSpeechRecognition()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoFile?.let { file ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val uri = android.net.Uri.fromFile(file)
                    val result = recognitionService.recognizeItem(uri)

                    if (result.isContainer) {
                        showContainerDetectionDialog(result)
                    } else {
                        handleSingleItemDetection(result)
                    }
                }
            }
        }
    }

    private val takeScenePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoFile?.let { file ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val uri = android.net.Uri.fromFile(file)
                    val sceneResult = recognitionService.recognizeScene(uri)
                    
                    if (sceneResult.objects.isNotEmpty()) {
                        viewModel.syncScene(sceneResult)
                        android.widget.Toast.makeText(requireContext(), 
                            "Scene scanned! Created Room and found ${sceneResult.objects.count { !it.isContainer }} items.", 
                            android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        // Even if 0 objects found, try to add it as a "New Room"
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("No Objects Detected")
                            .setMessage("I couldn't identify specific objects. Do you want to add this photo as a generic 'New Room'?")
                            .setPositiveButton("Add Room") { _, _ ->
                                viewModel.syncScene(com.example.placemate.core.input.SceneRecognitionResult(
                                    listOf(com.example.placemate.core.input.RecognizedObject("New Scanned Room", true, 1.0f))
                                ))
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
        }
    }

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

        // Force cleanup of bug data on load
        viewModel.repairData()

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

        binding.btnVisualSearch.setOnClickListener {
            startVisualSearch(false)
        }

        binding.btnSceneScan.setOnClickListener {
            startVisualSearch(true)
        }

        binding.btnClearData.setOnClickListener {
            showClearDataConfirmation()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { items ->
                    adapter.submitList(items)
                    binding.tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        arguments?.getString("searchQuery")?.let { query ->
            binding.searchEditText.setText(query)
            viewModel.updateSearchQuery(query)
        }
    }

    private fun showContainerDetectionDialog(result: com.example.placemate.core.input.RecognitionResult) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Location Detected")
            .setMessage("I detected a '${result.suggestedName}'. Do you want to add this as a new Location?")
            .setPositiveButton("Add Location") { _, _ ->
                val bundle = Bundle().apply { 
                    putString("openAddDialogName", result.suggestedName)
                }
                findNavController().navigate(R.id.nav_locations, bundle)
            }
            .setNegativeButton("Search as Item") { _, _ ->
                handleSingleItemDetection(result)
            }
            .show()
    }

    private fun handleSingleItemDetection(result: com.example.placemate.core.input.RecognitionResult) {
        result.suggestedName?.let { name ->
            binding.searchEditText.setText(name)
            viewModel.updateSearchQuery(name)
        }
    }

    private fun startVisualSearch(isScene: Boolean) {
        when {
            androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                launchCamera(isScene)
            }
            else -> {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera(isScene: Boolean) {
        photoFile = ImageUtils.createImageFile(requireContext())
        val uri = ImageUtils.getContentUri(requireContext(), photoFile!!)
        if (isScene) {
            takeScenePictureLauncher.launch(uri)
        } else {
            takePictureLauncher.launch(uri)
        }
    }

    private fun startSpeechSearch() {
        when {
            androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                launchSpeechRecognition()
            }
            else -> {
                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun launchSpeechRecognition() {
        viewLifecycleOwner.lifecycleScope.launch {
            speechManager.startListening().collect { state ->
                if (state is com.example.placemate.core.input.SpeechState.Result) {
                    val normalizedText = synonymManager.getRepresentativeName(state.text)
                    binding.searchEditText.setText(state.text) // Keep original text in UI
                    viewModel.updateSearchQuery(normalizedText) // Search with normalized term
                }
            }

        }
    }

    private fun showClearDataConfirmation() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear All Data")
            .setMessage("This will permanently delete ALL items and locations. Are you sure?")
            .setPositiveButton("Clear Everything") { _, _ ->
                viewModel.clearAllData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
