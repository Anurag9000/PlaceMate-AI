package com.example.placemate.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.placemate.R
import com.example.placemate.core.utils.ImageUtils
import com.example.placemate.data.local.entities.ItemStatus
import com.example.placemate.databinding.FragmentItemDetailBinding
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@AndroidEntryPoint
class ItemDetailFragment : Fragment() {

    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ItemDetailViewModel by viewModels()

    private var photoFile: File? = null
    private var captureUri: android.net.Uri? = null
    private var tempPhotoUri: String? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = captureUri
        if (success && uri != null) {
            binding.itemDetailImage.setImageURI(uri)
            tempPhotoUri = uri.toString()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

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
            viewModel.updateItemDetails(
                name = binding.editItemName.text?.toString().orEmpty(),
                category = binding.editItemCategory.text?.toString().orEmpty(),
                description = binding.editItemNotes.text?.toString(),
                photoUri = tempPhotoUri
            )
            Toast.makeText(requireContext(), "Item updated!", Toast.LENGTH_SHORT).show()
        }

        binding.fabEditImage.setOnClickListener {
            photoFile = ImageUtils.createImageFile(requireContext())
            captureUri = photoFile?.let { ImageUtils.getContentUri(requireContext(), it) }
            captureUri?.let(takePictureLauncher::launch)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.item.collect { item ->
                        item ?: return@collect
                        if (binding.editItemName.text.isNullOrEmpty()) {
                            binding.editItemName.setText(item.name)
                        }
                        if (binding.editItemCategory.text.isNullOrEmpty()) {
                            binding.editItemCategory.setText(item.category)
                        }
                        if (binding.editItemNotes.text.isNullOrEmpty()) {
                            binding.editItemNotes.setText(item.description)
                        }

                        binding.textItemStatus.text = item.status.name

                        if (tempPhotoUri == null) {
                            tempPhotoUri = item.photoUri
                        }
                        item.photoUri?.takeIf { it.isNotBlank() }?.let {
                            binding.itemDetailImage.setImageURI(android.net.Uri.parse(it))
                        }

                        if (item.status == ItemStatus.PRESENT) {
                            binding.btnAction.text = getString(R.string.btn_mark_taken)
                            binding.textItemStatus.setBackgroundResource(R.color.success)
                        } else {
                            binding.btnAction.text = getString(R.string.btn_mark_returned)
                            binding.textItemStatus.setBackgroundResource(R.color.error)
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
        val borrowerInput = dialogView.findViewById<TextInputEditText>(R.id.borrower_edit_text)
        val dueDateText = dialogView.findViewById<TextView>(R.id.text_due_date)
        var selectedDueDate: Long? = null

        dueDateText.setOnClickListener {
            val calendar = Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    selectedDueDate = calendar.timeInMillis
                    dueDateText.text =
                        android.text.format.DateFormat.getMediumDateFormat(requireContext())
                            .format(calendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.btn_mark_taken)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val borrower = borrowerInput.text?.toString().orEmpty().ifBlank { "Me" }
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
