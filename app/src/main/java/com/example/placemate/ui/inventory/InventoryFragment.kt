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
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType

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

    @javax.inject.Inject
    lateinit var categoryManager: com.example.placemate.core.utils.CategoryManager

    @javax.inject.Inject
    lateinit var configManager: com.example.placemate.core.utils.ConfigManager

    private var photoFile: File? = null
    private var pendingIsScene: Boolean = false


    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera(pendingIsScene)
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

                    if (result.errorMessage != null) {
                        showErrorDialog("Recognition Error", result.errorMessage)
                    } else if (result.isContainer) {
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
                    
                    if (sceneResult.errorMessage != null) {
                        showErrorDialog("Scan Error", sceneResult.errorMessage)
                    } else if (sceneResult.objects.isNotEmpty()) {
                        viewModel.syncScene(requireContext(), sceneResult, uri)
                        android.widget.Toast.makeText(requireContext(), 
                            "Scene scanned! Created Room and found ${sceneResult.objects.count { !it.isContainer }} items.", 
                            android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        // Truly 0 objects found by the AI
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("No Objects Detected")
                            .setMessage("The AI couldn't identify specific objects in this photo. Do you want to add this as a generic 'New Room' anyway?")
                            .setPositiveButton("Add Room") { _, _ ->
                                viewModel.syncScene(requireContext(), com.example.placemate.core.input.SceneRecognitionResult(
                                    listOf(com.example.placemate.core.input.RecognizedObject("New Scanned Room", true, 1.0f))
                                ), uri)
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

        val adapter = InventoryAdapter(
            onItemClick = { item ->
                val bundle = Bundle().apply { putString("itemId", item.id) }
                findNavController().navigate(R.id.nav_item_detail, bundle)
            },
            onFolderClick = { location ->
                viewModel.navigateTo(location.id)
            },
            onFolderLongClick = { location ->
                showLocationDialog(location)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Back Press handling for folder up navigation
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.currentLocationId.value != null) {
                    viewModel.navigateUp()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressed()
                }
            }
        })

        binding.searchEditText.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.nav_add_item)
        }

        binding.fabAddFolder.setOnClickListener {
            showLocationDialog(null)
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

        binding.cardOmniSearch.setOnClickListener {
            findNavController().navigate(R.id.nav_omni_search)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect Explorer State
                launch {
                    viewModel.explorerItems.collect { items ->
                        // If no items in explorer (and not searching?), show empty state?
                        if (binding.searchEditText.text.isNullOrEmpty()) {
                           adapter.submitList(items)
                           binding.tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                           binding.tvEmptyState.text = if (viewModel.currentLocationId.value == null) "No items yet. Scan something!" else "This location is empty."
                        }
                    }
                }
                
                // Collect Current Location for UI Update (Breadcrumb)
                launch {
                    viewModel.currentPath.collect { path ->
                        binding.tvBreadcrumb.text = if (binding.searchEditText.text.isNullOrEmpty()) path else "Searching..."
                    }
                }
            }
        }

        arguments?.getString("searchQuery")?.let { query ->
            binding.searchEditText.setText(query)
            viewModel.updateSearchQuery(query)
        }

        arguments?.getString("locationId")?.let { locId ->
            viewModel.navigateTo(locId)
        }

        val menuHost: androidx.core.view.MenuHost = requireActivity()
        menuHost.addMenuProvider(object : androidx.core.view.MenuProvider {
            override fun onCreateMenu(menu: android.view.Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(R.menu.overflow, menu)
            }

            override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean {
                return if (menuItem.itemId == R.id.nav_settings) {
                    findNavController().navigate(R.id.nav_settings)
                    true
                } else false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showContainerDetectionDialog(result: com.example.placemate.core.input.RecognitionResult) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Location Detected")
            .setMessage("I detected a '${result.suggestedName}'. Do you want to add this as a new Location?")
            .setPositiveButton("Add Location") { _, _ ->
                showLocationDialog(null, result.suggestedName)
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
        pendingIsScene = isScene
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
        
        val engineName = if (configManager.isGeminiEnabled() && configManager.hasGeminiApiKey()) "Gemini" else "ML Kit"
        android.widget.Toast.makeText(requireContext(), "Starting $engineName Scan...", android.widget.Toast.LENGTH_SHORT).show()

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

    private fun showLocationDialog(locationToEdit: com.example.placemate.data.local.entities.LocationEntity?, initialName: String? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_location, null)
        val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.location_name_edit_text)
        nameInput.setText(locationToEdit?.name ?: initialName ?: "")
        
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.type_spinner)
        val parentSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.parent_spinner)

        // Setup type spinner
        val types = com.example.placemate.data.local.entities.LocationType.values()
        typeSpinner.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types.map { it.name })
        locationToEdit?.let { typeSpinner.setSelection(types.indexOf(it.type)) }

        // Setup parent spinner
        viewLifecycleOwner.lifecycleScope.launch {
            val allLocations = viewModel.getAllLocations()
            val filteredLocations = if (locationToEdit != null) allLocations.filter { it.id != locationToEdit.id } else allLocations
            val parentNames = mutableListOf("None")
            parentNames.addAll(filteredLocations.map { it.name })
            parentSpinner.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, parentNames)
            
            // Default to current location if adding new
            val currentLocId = viewModel.currentLocationId.value
            val initialParentId = locationToEdit?.parentId ?: currentLocId
            val parentIndex = filteredLocations.indexOfFirst { it.id == initialParentId }
            if (parentIndex >= 0) parentSpinner.setSelection(parentIndex + 1)

            android.app.AlertDialog.Builder(requireContext())
                .setTitle(if (locationToEdit == null) "Add Folder" else "Edit Folder")
                .setView(dialogView)
                .setPositiveButton(if (locationToEdit == null) "Add" else "Update") { _, _ ->
                    val name = nameInput.text?.toString() ?: return@setPositiveButton
                    val type = types[typeSpinner.selectedItemPosition]
                    val pIndex = parentSpinner.selectedItemPosition
                    val pId = if (pIndex == 0) null else filteredLocations[pIndex - 1].id

                    if (locationToEdit == null) {
                        viewModel.addLocation(name, type, pId)
                        android.widget.Toast.makeText(requireContext(), "Folder created!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateLocation(locationToEdit.id, name, type, pId)
                        android.widget.Toast.makeText(requireContext(), "Folder updated!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
