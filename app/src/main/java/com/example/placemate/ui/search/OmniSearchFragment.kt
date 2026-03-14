package com.example.placemate.ui.search

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.placemate.R
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.core.input.SpeechManager
import com.example.placemate.core.input.SpeechState
import com.example.placemate.core.utils.ImageUtils
import com.example.placemate.databinding.FragmentOmniSearchBinding
import com.example.placemate.ui.inventory.InventoryAdapter
import com.example.placemate.ui.inventory.InventoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class OmniSearchFragment : Fragment() {

    private var _binding: FragmentOmniSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventoryViewModel by viewModels()

    @Inject
    lateinit var speechManager: SpeechManager

    @Inject
    lateinit var recognitionService: ItemRecognitionService

    private var photoFile: File? = null
    private var captureUri: android.net.Uri? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchSpeechRecognition()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = captureUri
        if (success && uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                binding.progressBar.visibility = View.VISIBLE
                val hint = viewModel.getLocationContextHint()
                val result = recognitionService.recognizeItem(uri, hint)
                binding.progressBar.visibility = View.GONE
                result.suggestedName?.let { name ->
                    binding.etSearch.setText(name)
                    viewModel.updateSearchQuery(name)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
            onFolderLongClick = { }
        )

        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = adapter

        binding.etSearch.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString().orEmpty())
        }

        binding.btnVoiceSearch.setOnClickListener { startVoiceSearch() }
        binding.btnCameraSearch.setOnClickListener { startCameraSearch() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.explorerItems.collect { items ->
                    adapter.submitList(items)
                    val query = binding.etSearch.text?.toString().orEmpty()
                    binding.tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvEmptyState.text =
                        if (query.isEmpty()) "Try searching for something..." else "No matches found."
                }
            }
        }
    }

    private fun startCameraSearch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        photoFile = ImageUtils.createImageFile(requireContext())
        captureUri = photoFile?.let { ImageUtils.getContentUri(requireContext(), it) }
        captureUri?.let(takePictureLauncher::launch)
    }

    private fun startVoiceSearch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchSpeechRecognition()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchSpeechRecognition() {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                speechManager.startListening().collect { state ->
                    when (state) {
                        is SpeechState.Result -> {
                            binding.progressBar.visibility = View.GONE
                            binding.etSearch.setText(state.text)
                            viewModel.updateSearchQuery(state.text)
                        }
                        is SpeechState.Error -> binding.progressBar.visibility = View.GONE
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
