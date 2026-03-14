package com.example.placemate.ui.locations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
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
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType
import com.example.placemate.databinding.FragmentLocationsBinding
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LocationsFragment : Fragment() {

    private var _binding: FragmentLocationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocationsViewModel by viewModels()

    @Inject
    lateinit var speechManager: SpeechManager

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

        val adapter = LocationAdapter(
            onItemClick = { itemWithCount ->
                val bundle = Bundle().apply { putString("locationId", itemWithCount.location.id) }
                findNavController().navigate(R.id.nav_inventory, bundle)
            },
            onItemLongClick = { itemWithCount ->
                showLocationDialog(itemWithCount.location)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnSpeechLocations.setOnClickListener { startSpeechLocation() }
        binding.fabAddLocation.setOnClickListener { showLocationDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.locations.collect { locations ->
                    adapter.submitList(locations)
                }
            }
        }

        arguments?.getString("openAddDialogName")?.let { name ->
            showLocationDialog(initialName = name)
            arguments?.remove("openAddDialogName")
        }
    }

    private fun startSpeechLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                speechManager.startListening().collect { state ->
                    if (state is SpeechState.Result) {
                        showLocationDialog(initialName = state.text)
                    }
                }
            }
        }
    }

    private fun showLocationDialog(locationToEdit: LocationEntity? = null, initialName: String = "") {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_location, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.location_name_edit_text)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.type_spinner)
        val parentSpinner = dialogView.findViewById<Spinner>(R.id.parent_spinner)

        nameInput.setText(locationToEdit?.name ?: initialName)

        val types = LocationType.values()
        typeSpinner.adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            types.map { it.name }
        )
        locationToEdit?.let { typeSpinner.setSelection(types.indexOf(it.type)) }

        val locations = viewModel.locations.value.map { it.location }.filter { it.id != locationToEdit?.id }
        val parentNames = mutableListOf("None").apply { addAll(locations.map { it.name }) }
        parentSpinner.adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            parentNames
        )
        locationToEdit?.let { loc ->
            val parentIndex = locations.indexOfFirst { it.id == loc.parentId }
            if (parentIndex >= 0) {
                parentSpinner.setSelection(parentIndex + 1)
            }
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(if (locationToEdit != null) "Edit Location" else "Add Location")
            .setView(dialogView)
            .setPositiveButton(if (locationToEdit != null) "Update" else "Add") { _, _ ->
                val name = nameInput.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    return@setPositiveButton
                }

                val type = types[typeSpinner.selectedItemPosition]
                val parentIndex = parentSpinner.selectedItemPosition
                val parentId = if (parentIndex == 0) null else locations[parentIndex - 1].id

                if (locationToEdit != null) {
                    viewModel.updateLocation(locationToEdit.id, name, type, parentId)
                } else {
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
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDuplicateWarning(
        existingLocation: LocationEntity,
        items: List<ItemEntity>,
        onConfirm: () -> Unit
    ) {
        val message = buildString {
            append("Location '${existingLocation.name}' already exists.\n\n")
            if (items.isEmpty()) {
                append("It is currently empty.")
            } else {
                append("It contains ${items.size} items:\n")
                items.take(5).forEach { item -> append("- ${item.name}\n") }
                if (items.size > 5) {
                    append("...and ${items.size - 5} more.")
                }
            }
            append("\n\nAre you sure you want to create a new duplicate?")
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Duplicate Location Warning")
            .setMessage(message)
            .setPositiveButton("Create Anyway") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
