package com.example.placemate.ui.inventory

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.placemate.core.utils.ImageUtils
import com.example.placemate.databinding.FragmentAddItemBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class AddItemFragment : Fragment() {

    private var _binding: FragmentAddItemBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddItemViewModel by viewModels()

    private var photoFile: File? = null
    private var photoUri: Uri? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePicture()
        } else {
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startSpeechInput()
        } else {
            Toast.makeText(requireContext(), "Audio permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            binding.btnTakePhoto.setImageURI(photoUri)
            viewModel.onImagePicked(photoUri!!)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.nameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.onNameChanged(binding.nameEditText.text.toString())
        }
        binding.categoryEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.onCategoryChanged(binding.categoryEditText.text.toString())
        }
        binding.notesEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.onNotesChanged(binding.notesEditText.text.toString())
        }

        binding.btnSpeechName.setOnClickListener {
            checkAndRequestAudioPermission()
        }

        binding.btnTakePhoto.setOnClickListener {
            checkAndRequestCameraPermission()
        }

        binding.btnEditLocation.setOnClickListener {
            showHierarchicalLocationDialog()
        }

        binding.btnSave.setOnClickListener {
            viewModel.onNameChanged(binding.nameEditText.text.toString())
            viewModel.onCategoryChanged(binding.categoryEditText.text.toString())
            viewModel.onNotesChanged(binding.notesEditText.text.toString())
            viewModel.saveItem()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.tvSelectedLocation.text = state.locationPath
                        
                        if (state.name != binding.nameEditText.text.toString()) {
                            binding.nameEditText.setText(state.name)
                        }
                        if (state.category != binding.categoryEditText.text.toString()) {
                            binding.categoryEditText.setText(state.category)
                        }
                        if (state.isSaved) {
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }

    }

    private fun showHierarchicalLocationDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "e.g. Living Room > Drawer > Box"
            setText(viewModel.uiState.value.locationPath.takeIf { it != "Not Set" } ?: "")
        }
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Set Hierarchical Location")
            .setMessage("Enter the path separated by '>'")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val pathStr = input.text.toString()
                if (pathStr.isNotEmpty()) {
                    val path = pathStr.split(">").map { it.trim() }.filter { it.isNotEmpty() }
                    viewModel.setLocationPath(path)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePicture()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startSpeechInput()
        } else {
            requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun takePicture() {
        photoFile = ImageUtils.createImageFile(requireContext())
        photoUri = ImageUtils.getContentUri(requireContext(), photoFile!!)
        takePictureLauncher.launch(photoUri)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
