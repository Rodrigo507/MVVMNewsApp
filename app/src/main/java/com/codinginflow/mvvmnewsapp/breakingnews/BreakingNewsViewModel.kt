package com.codinginflow.mvvmnewsapp.breakingnews

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.codinginflow.mvvmnewsapp.data.NewsArticle
import com.codinginflow.mvvmnewsapp.data.NewsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class BreakingNewsViewModel @ViewModelInject constructor(
    private val repository: NewsRepository
) : ViewModel() {

    private val refreshTrigger = MutableLiveData<Refresh>()

    private val eventChannel = Channel<Event>()
    val events = eventChannel.receiveAsFlow()

    /*val breakingNews = refreshTrigger.switchMap { refresh ->
        Timber.d("forceRefresh = ${Refresh.FORCE == refresh}")
        repository.getBreakingNews(
            Refresh.FORCE == refresh, // this direction makes it Java null-safe
            onFetchFailed = { t ->
                showErrorMessage(t)
            }
        ).asLiveData()
    }*/

    fun onStart() {
        Timber.d("Fragment onStart")
        refreshTrigger.value = Refresh.NORMAL
    }

    fun onManualRefresh() {
        Timber.d("onManualRefresh()")
        refreshTrigger.value = Refresh.FORCE
    }

    fun onBookmarkClick(article: NewsArticle) {
        val currentlyBookmarked = article.isBookmarked
        val updatedArticle = article.copy(isBookmarked = !currentlyBookmarked)
        viewModelScope.launch {
            repository.update(updatedArticle)
        }
    }

    private fun showErrorMessage(t: Throwable) = viewModelScope.launch {
        eventChannel.send(Event.ShowErrorMessage(t))
    }

    enum class Refresh {
        FORCE, NORMAL
    }

    sealed class Event {
        data class ShowErrorMessage(val throwable: Throwable) : Event()
    }
}