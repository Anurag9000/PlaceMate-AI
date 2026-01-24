import com.example.placemate.ui.inventory.ExplorerItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first

@HiltViewModel
class TakenItemsViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    val takenItems: StateFlow<List<ExplorerItem>> = combine(
        repository.getTakenItems(),
        _searchQuery
    ) { items, query ->
        val filtered = if (query.isBlank()) items
        else items.filter { it.name.contains(query, ignoreCase = true) }
        
        filtered.map { item ->
            // In a real app with many items, we'd use a more optimized join query.
            // For MVP, mapping with repo helper is sufficient.
            val path = repository.getLocationPathForItem(item.id)
            ExplorerItem.File(item, path)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
