package com.kazahana.app.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kazahana.app.data.model.PostView
import com.kazahana.app.data.repository.PostRepository
import com.kazahana.app.ui.navigation.QuotesListRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuotesListUiState(
    val posts: List<PostView> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val cursor: String? = null,
)

@HiltViewModel
class QuotesListViewModel @Inject constructor(
    private val postRepository: PostRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val postUri: String = savedStateHandle.toRoute<QuotesListRoute>().postUri

    private val _uiState = MutableStateFlow(QuotesListUiState())
    val uiState: StateFlow<QuotesListUiState> = _uiState.asStateFlow()

    init {
        loadQuotes()
    }

    private fun loadQuotes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            postRepository.getQuotes(postUri)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            posts = response.posts,
                            isLoading = false,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message)
                    }
                }
        }
    }

    fun loadMore() {
        val currentCursor = _uiState.value.cursor ?: return
        if (_uiState.value.isLoadingMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            postRepository.getQuotes(postUri, cursor = currentCursor)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            posts = it.posts + response.posts,
                            isLoadingMore = false,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
        }
    }
}
