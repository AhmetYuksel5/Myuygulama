package com.ahmety.uygulama.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Entry> = emptyList(),
    val searched: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: EntryRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    /**
     * Her tuşa basışta veritabanına gitmemek için kısa bir bekleme koyuyoruz;
     * `mapLatest` de bekleme dolmadan gelen yeni sorguda eskisini iptal ediyor.
     */
    val uiState: StateFlow<SearchUiState> = queryFlow
        .debounce(SEARCH_DEBOUNCE_MS)
        .mapLatest { query ->
            SearchUiState(
                query = query,
                results = if (query.isBlank()) emptyList() else repository.search(query),
                searched = query.isNotBlank(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        queryFlow.value = value
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
