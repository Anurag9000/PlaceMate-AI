import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.placemate.core.input.SpeechState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import android.net.Uri

@AndroidEntryPoint
class OmniSearchFragment : Fragment() {

    private var _binding: FragmentOmniSearchBinding? = null
    private val binding get() = _binding!!

    // We can reuse InventoryViewModel for search logic or create a dedicated one
    private val viewModel: InventoryViewModel by viewModels()

    @javax.inject.Inject
    lateinit var speechManager: com.example.placemate.core.input.SpeechManager

    @javax.inject.Inject
    lateinit var recognitionService: ItemRecognitionService

    private var photoFile: File? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) launchCamera()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) launchSpeechRecognition()
    }

    private val takePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
                viewLifecycleOwner.lifecycleScope.launch {
                    binding.progressBar.visibility = View.VISIBLE
                    val uri = Uri.fromFile(file)
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
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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
            onFolderLongClick = { _ -> }
        )

        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = adapter

        binding.etSearch.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }

        binding.btnVoiceSearch.setOnClickListener { startVoiceSearch() }
        binding.btnCameraSearch.setOnClickListener { startCameraSearch() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.explorerItems.collect { items ->
                    adapter.submitList(items)
                    val query = binding.etSearch.text.toString()
                    binding.tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvEmptyState.text = if (query.isEmpty()) "Try searching for something..." else "No matches found."
                }
            }
        }
    }

    private fun startCameraSearch() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        photoFile = ImageUtils.createImageFile(requireContext())
        val uri = ImageUtils.getContentUri(requireContext(), photoFile!!)
        takePictureLauncher.launch(uri)
    }

    private fun startVoiceSearch() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognition()
        } else {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchSpeechRecognition() {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                speechManager.startListening().collect { state ->
                    when (state) {
                        is SpeechState.Result -> {
                            binding.progressBar.visibility = View.GONE
                            binding.etSearch.setText(state.text)
                            viewModel.updateSearchQuery(state.text)
                        }
                        is SpeechState.Error -> {
                            binding.progressBar.visibility = View.GONE
                        }
                        else -> {}
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
