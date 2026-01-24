import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.core.input.SpeechState
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
        
            onItemClick = { itemWithCount ->
                val bundle = Bundle().apply { putString("locationId", itemWithCount.location.id) }
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.nav_inventory, bundle)
            },
            onItemLongClick = { itemWithCount ->
                showLocationDialog(itemWithCount.location)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnSpeechLocations.setOnClickListener {
            startSpeechLocation()
        }

        binding.fabAddLocation.setOnClickListener {
            showLocationDialog()
        }

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
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
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
        val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.location_name_edit_text)
        nameInput.setText(locationToEdit?.name ?: initialName)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.type_spinner)
        val parentSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.parent_spinner)

        // Setup type spinner
        val types = LocationType.values()
        typeSpinner.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types.map { it.name })
        locationToEdit?.let { typeSpinner.setSelection(types.indexOf(it.type)) }

        // Setup parent spinner
        val locations = viewModel.locations.value.map { it.location }.filter { it.id != locationToEdit?.id }
        val parentNames = mutableListOf("None")
        parentNames.addAll(locations.map { it.name })
        parentSpinner.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, parentNames)
        locationToEdit?.let { loc ->
            val parentIndex = locations.indexOfFirst { it.id == loc.parentId }
            if (parentIndex >= 0) parentSpinner.setSelection(parentIndex + 1)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(if (locationToEdit != null) "Edit Location" else "Add Location")
            .setView(dialogView)
            .setPositiveButton(if (locationToEdit != null) "Update" else "Add") { _, _ ->
                val name = nameInput.text?.toString() ?: return@setPositiveButton
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
