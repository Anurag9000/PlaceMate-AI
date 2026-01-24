import android.net.Uri
import java.io.File
import com.example.placemate.core.utils.ImageUtils
import androidx.activity.result.contract.ActivityResultContracts

@AndroidEntryPoint
class ItemDetailFragment : Fragment() {

    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ItemDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var photoFile: File? = null
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoFile?.let { file ->
                val uri = Uri.fromFile(file)
                binding.itemDetailImage.setImageURI(uri)
                tempPhotoUri = uri.toString()
            }
        }
    }
    private var tempPhotoUri: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val itemId = arguments?.getString("itemId") ?: return
        viewModel.loadItem(itemId)

        binding.btnAction.setOnClickListener {
            val item = viewModel.item.value ?: return@setOnClickListener
            if (item.status == ItemStatus.PRESENT) {
                showMarkTakenDialog()
            } else {
                viewModel.markAsReturned()
            }
        }

        binding.btnDelete.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete this item?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteItem()
                    findNavController().popBackStack()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnSave.setOnClickListener {
            val name = binding.editItemName.text?.toString() ?: ""
            val category = binding.editItemCategory.text?.toString() ?: ""
            val notes = binding.editItemNotes.text?.toString()
            viewModel.updateItemDetails(name, category, notes, tempPhotoUri)
            android.widget.Toast.makeText(requireContext(), "Item updated!", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.fabEditImage.setOnClickListener {
            photoFile = ImageUtils.createImageFile(requireContext())
            val uri = ImageUtils.getContentUri(requireContext(), photoFile!!)
            takePictureLauncher.launch(uri)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.item.collect { item ->
                        item?.let {
                            if (binding.editItemName.text.isNullOrEmpty()) {
                                binding.editItemName.setText(it.name)
                            }
                            if (binding.editItemCategory.text.isNullOrEmpty()) {
                                binding.editItemCategory.setText(it.category)
                            }
                            if (binding.editItemNotes.text.isNullOrEmpty()) {
                                binding.editItemNotes.setText(it.description)
                            }
                            
                            binding.textItemStatus.text = it.status.name
                            
                            if (!it.photoUri.isNullOrEmpty()) {
                                binding.itemDetailImage.setImageURI(android.net.Uri.parse(it.photoUri))
                            }

                            if (it.status == ItemStatus.PRESENT) {
                                binding.btnAction.text = getString(R.string.btn_mark_taken)
                                binding.textItemStatus.setBackgroundResource(R.color.success)
                            } else {
                                binding.btnAction.text = getString(R.string.btn_mark_returned)
                                binding.textItemStatus.setBackgroundResource(R.color.error)
                            }
                        }
                    }
                }
                launch {
                    viewModel.locationPath.collect { path ->
                        binding.textItemLocation.text = "Location: ${path ?: "Unknown"}"
                    }
                }
            }
        }
    }

    private fun showMarkTakenDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_mark_taken, null)
        val borrowerInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.borrower_edit_text)
        val dueDateText = dialogView.findViewById<android.widget.TextView>(R.id.text_due_date)
        var selectedDueDate: Long? = null

        dueDateText.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                calendar.set(year, month, day)
                selectedDueDate = calendar.timeInMillis
                dueDateText.text = android.text.format.DateFormat.getMediumDateFormat(requireContext()).format(calendar.time)
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.btn_mark_taken)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val borrower = borrowerInput.text?.toString() ?: "Me"
                viewModel.markAsTaken(borrower, selectedDueDate)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
