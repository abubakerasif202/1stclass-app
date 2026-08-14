package au.com.firstclassexpress.driver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ViewModelFactory<VM : ViewModel>(
    private val creator: () -> VM
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

fun <VM : ViewModel> viewModelFactory(creator: () -> VM): ViewModelProvider.Factory =
    ViewModelFactory(creator)
