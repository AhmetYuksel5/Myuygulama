package com.ahmety.uygulama.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Widget ve paylaşım gibi dış giriş noktalarından gelen "şu ekrana git"
 * isteklerini arayüze taşır. Intent doğrudan Compose'a ulaşamadığı için
 * aradaki köprü bu.
 */
object NavRequestBus {

    private val _target = MutableStateFlow<String?>(null)
    val target = _target.asStateFlow()

    fun request(target: String) {
        _target.value = target
    }

    fun consume() {
        _target.value = null
    }

    const val TARGET_TASKS = "gorevler"
    const val TARGET_ADD_TASK = "gorev_ekle"
}
