package com.spotitfly.app.ui.spots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.spotitfly.app.data.SpotsRepository

class SpotsListViewModelFactory(
    private val repo: SpotsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpotsListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpotsListViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
