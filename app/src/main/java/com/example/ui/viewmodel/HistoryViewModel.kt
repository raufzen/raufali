package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CommandHistoryEntity
import com.example.data.repository.CommandHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL, OFFLINE, AI_AGENT, FAILED
}

data class HistoryUiState(
    val searchQuery: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val items: List<CommandHistoryEntity> = emptyList()
)

class HistoryViewModel(
    private val repository: CommandHistoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(HistoryFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = combine(
        _searchQuery,
        _filter,
        _searchQuery.flatMapLatest { q -> repository.searchHistory(q) }
    ) { query, filter, list ->
        val filtered = when (filter) {
            HistoryFilter.ALL -> list
            HistoryFilter.OFFLINE -> list.filter { it.executionMode == "OFFLINE" }
            HistoryFilter.AI_AGENT -> list.filter { it.executionMode == "AI_AGENT" }
            HistoryFilter.FAILED -> list.filter { it.status == "FAILED" || it.status == "NEEDS_PERMISSION" }
        }
        HistoryUiState(query, filter, filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun selectFilter(filter: HistoryFilter) {
        _filter.value = filter
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    companion object {
        fun factory(repository: CommandHistoryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(repository) as T
                }
            }
    }
}
