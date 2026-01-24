import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.placemate.R
import com.example.placemate.databinding.FragmentSentinelBinding
import com.example.placemate.core.utils.ImageUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class SentinelFragment : Fragment() {

    private var _binding: FragmentSentinelBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SentinelViewModel by viewModels()
    private val auditAdapter = AuditAdapter()
    
    private var photoFile: File? = null
    private var selectedLocationId: String? = null

    private val takePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val locId = selectedLocationId
        if (success && photoFile != null && locId != null) {
            viewModel.performAudit(android.net.Uri.fromFile(photoFile), locId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSentinelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.rvAuditResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = auditAdapter
        }
    }

    private fun setupListeners() {
        binding.btnScanAudit.setOnClickListener {
            if (selectedLocationId == null) {
                Toast.makeText(requireContext(), getString(R.string.audit_error_select_room), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            launchCamera()
        }
    }

    private fun launchCamera() {
        photoFile = ImageUtils.createImageFile(requireContext())
        photoFile?.let {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                it
            )
            takePictureLauncher.launch(uri)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.locations.collect { locations ->
                        val roomNames = locations.map { it.name }
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roomNames)
                        binding.roomSelector.setAdapter(adapter)
                        
                        binding.roomSelector.setOnItemClickListener { _, _, position, _ ->
                            selectedLocationId = locations[position].id
                        }
                    }
                }

                launch {
                    viewModel.auditResults.collect { results ->
                        auditAdapter.submitList(results)
                        binding.tvEmptyState.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                        binding.btnScanAudit.isEnabled = !loading
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
