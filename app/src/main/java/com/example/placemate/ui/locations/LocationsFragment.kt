package com.example.placemate.ui.locations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.placemate.R
import com.example.placemate.databinding.FragmentLocationsBinding

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LocationsFragment : Fragment() {

    private var _binding: FragmentLocationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocationsViewModel by viewModels()

    @javax.inject.Inject
    lateinit var speechManager: com.example.placemate.core.input.SpeechManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val adapter = LocationAdapter { location ->
            // Navigate to InventoryFragment with locationId for drill-down
            val bundle = Bundle().apply { putString("locationId", location.id) }
            androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.nav_inventory, bundle)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnSpeechLocations.setOnClickListener {
            startSpeechLocation()
        }

        binding.fabAddLocation.setOnClickListener {
            showAddLocationDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.locations.collect { locations ->
                    adapter.submitList(locations)
                }
            }
        }

        arguments?.getString("openAddDialogName")?.let { name ->
            showAddLocationDialog(name)
            arguments?.remove("openAddDialogName")
        }
    }



    private fun startSpeechLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            speechManager.startListening().collect { state ->
                if (state is com.example.placemate.core.input.SpeechState.Result) {
                    showAddLocationDialog(state.text)
                }
            }
        }
    }

    private fun showAddLocationDialog(initialName: String = "") {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_location, null)
        val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.location_name_edit_text)
        nameInput.setText(initialName)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.type_spinner)
        val parentSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.parent_spinner)

        // Setup type spinner
        val types = com.example.placemate.data.local.entities.LocationType.values()
        typeSpinner.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types.map { it.name })

        // Setup parent spinner
        val locations = viewModel.locations.value
        val parentNames = mutableListOf("None")
        parentNames.addAll(locations.map { it.name })
        parentSpinner.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, parentNames)

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Location")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text?.toString() ?: return@setPositiveButton
                val type = types[typeSpinner.selectedItemPosition]
                val parentIndex = parentSpinner.selectedItemPosition
                val parentId = if (parentIndex == 0) null else locations[parentIndex - 1].id

                val existingLocation = viewModel.checkLocationExists(name)
                if (existingLocation != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val items = viewModel.getItemsForLocation(existingLocation.id)
                        showDuplicateWarning(existingLocation, items) {
                            viewModel.addLocation(name, type, parentId)
                        }
                    }
                } else {
                    viewModel.addLocation(name, type, parentId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDuplicateWarning(
        existingLocation: com.example.placemate.data.local.entities.LocationEntity,
        items: List<com.example.placemate.data.local.entities.ItemEntity>,
        onConfirm: () -> Unit
    ) {
        val message = StringBuilder()
        message.append("Location '${existingLocation.name}' already exists.\n\n")
        if (items.isEmpty()) {
            message.append("It is currently empty.")
        } else {
            message.append("It contains ${items.size} items:\n")
            items.take(5).forEach { item ->
                message.append("- ${item.name}\n")
            }
            if (items.size > 5) {
                message.append("...and ${items.size - 5} more.")
            }
        }
        message.append("\n\nAre you sure you want to create a new duplicate?")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Duplicate Location Warning")
            .setMessage(message.toString())
            .setPositiveButton("Create Anyway") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
