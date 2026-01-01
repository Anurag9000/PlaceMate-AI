package com.example.placemate.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.placemate.R
import com.example.placemate.data.local.entities.ItemStatus
import com.example.placemate.databinding.FragmentItemDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

    private var photoFile: java.io.File? = null
    private val takePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoFile?.let { file ->
                val uri = android.net.Uri.fromFile(file)
                binding.itemDetailImage.setImageURI(uri)
                // We'll save the URI when they click "Save"
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
            photoFile = com.example.placemate.core.utils.ImageUtils.createImageFile(requireContext())
            val uri = com.example.placemate.core.utils.ImageUtils.getContentUri(requireContext(), photoFile!!)
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
                                binding.textItemStatus.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                            } else {
                                binding.btnAction.text = getString(R.string.btn_mark_returned)
                                binding.textItemStatus.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
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
