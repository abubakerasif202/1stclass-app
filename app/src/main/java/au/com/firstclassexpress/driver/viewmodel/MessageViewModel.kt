package au.com.firstclassexpress.driver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.firstclassexpress.driver.domain.model.DriverMessage
import au.com.firstclassexpress.driver.domain.model.MessageCategory
import au.com.firstclassexpress.driver.domain.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MessageUiState(
    val messages: List<DriverMessage> = emptyList(),
    val unreadCount: Int = 0,
    val selectedCategory: MessageCategory? = null,
    val isLoading: Boolean = false
) {
    val filteredMessages: List<DriverMessage>
        get() = if (selectedCategory == null) messages else messages.filter { it.category == selectedCategory }
}

class MessageViewModel(
    private val messageRepository: MessageRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessageUiState())
    val uiState: StateFlow<MessageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            messageRepository.observeMessages().collect { list ->
                _uiState.update { it.copy(messages = list) }
            }
        }
        viewModelScope.launch {
            messageRepository.observeUnreadCount().collect { count ->
                _uiState.update { it.copy(unreadCount = count) }
            }
        }
    }

    fun selectCategory(category: MessageCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            messageRepository.markAsRead(messageId)
        }
    }
}
