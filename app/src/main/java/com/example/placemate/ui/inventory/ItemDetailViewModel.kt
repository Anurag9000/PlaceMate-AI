import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val trackingRepository: TrackingRepository,
    private val reminderManager: ReminderManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId = savedStateHandle.getStateFlow<String?>("itemId", null)

    val item: StateFlow<ItemEntity?> = itemId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else inventoryRepository.observeItemById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val locationPath: StateFlow<String?> = item.flatMapLatest { item ->
        if (item == null) flowOf(null)
        else kotlinx.coroutines.flow.flow {
            emit(inventoryRepository.getLocationPathForItem(item.id))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadItem(id: String) {
        savedStateHandle["itemId"] = id
    }

    fun markAsTaken(borrower: String, dueDate: Long?) {
        val currentItem = item.value ?: return
        viewModelScope.launch {
            inventoryRepository.markItemAsTaken(currentItem, borrower, dueDate)
        }
    }

    fun markAsReturned() {
        val currentItem = item.value ?: return
        viewModelScope.launch {
            inventoryRepository.markItemAsReturned(currentItem)
        }
    }

    fun deleteItem() {
        val currentItem = item.value ?: return
        viewModelScope.launch {
            inventoryRepository.deleteItem(currentItem)
        }
    }

    fun updateItemDetails(name: String, category: String, description: String?, photoUri: String?) {
        val currentItem = item.value ?: return
        viewModelScope.launch {
            val updatedItem = currentItem.copy(
                name = name,
                category = category,
                description = description,
                photoUri = photoUri ?: currentItem.photoUri,
                updatedAt = System.currentTimeMillis()
            )
            inventoryRepository.saveItem(updatedItem)
        }
    }
}
