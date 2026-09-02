package com.ahmety.uygulama.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.repository.VocabProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Alt çubuktaki "bugün kaç kelime" rozeti.
 *
 * Ayrı bir görünüm modeli çünkü rozet uygulamanın her ekranında duruyor;
 * kelime ekranının kendi modelini burada da kurmak, deste hiç açılmasa
 * bile bütün işaretlemeleri ve yapay zekâ dolgularını her değişiklikte
 * yeniden hesaplamak demekti. Rozetin ihtiyacı olan tek şey tekrar
 * satırlarının sayısı.
 */
@HiltViewModel
class DeckBadgeViewModel @Inject constructor(
    progress: VocabProgressRepository,
) : ViewModel() {

    val dueToday: StateFlow<Int> = progress.observeDueToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
