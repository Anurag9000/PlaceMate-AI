import androidx.core.widget.addTextChangedListener
import com.example.placemate.core.input.SpeechState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TakenItemsFragment : Fragment() {

    private var _binding: FragmentTakenItemsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TakenItemsViewModel by viewModels()

    @javax.inject.Inject
    lateinit var speechManager: com.example.placemate.core.input.SpeechManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTakenItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val adapter = InventoryAdapter(
            onItemClick = { item ->
                val bundle = Bundle().apply { putString("itemId", item.id) }
                findNavController().navigate(R.id.nav_item_detail, bundle)
            },
            onFolderClick = { _ -> }, // No folder navigation in Taken items
            onFolderLongClick = { _ -> } 
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnSpeechTaken.setOnClickListener {
            startSpeechSearch()
        }

        binding.cardOmniSearch.setOnClickListener {
            findNavController().navigate(R.id.nav_omni_search)
        }

        binding.searchEditText.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }

                viewModel.takenItems.collect { items ->
                    adapter.submitList(items)
                }
            }
        }
    }

    private fun startSpeechSearch() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                speechManager.startListening().collect { state ->
                    if (state is SpeechState.Result) {
                        binding.searchEditText.setText(state.text)
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
