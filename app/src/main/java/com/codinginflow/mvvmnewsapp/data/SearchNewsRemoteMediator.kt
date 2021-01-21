package com.codinginflow.mvvmnewsapp.data

import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.codinginflow.mvvmnewsapp.api.NewsApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

private const val NEWS_STARTING_PAGE_INDEX = 1

class SearchNewsRemoteMediator(
    private val searchQuery: String,
    private val newsDb: NewsArticleDatabase,
    private val newsApi: NewsApi
) : RemoteMediator<Int, NewsArticle>() {

    private val newsArticleDao = newsDb.newsArticleDao()

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, NewsArticle>
    ): MediatorResult {
        Timber.d("load with anchorPosition = ${state.anchorPosition}")
        val page = when (loadType) {
            LoadType.REFRESH -> {
                Timber.d("Start REFRESH")
                val nextPageKey = getNextPageKeyClosestToCurrentPosition(state)
                Timber.d("return REFRESH with nextKey = $nextPageKey")
                nextPageKey?.minus(1) ?: NEWS_STARTING_PAGE_INDEX
            }
            LoadType.PREPEND -> {
                Timber.d("Start PREPEND")
                val prevPageKey = getPreviousPageKeyForFirstItem(state)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                Timber.d("return PREPEND with prevPageKey = $prevPageKey")
                prevPageKey
            }
            LoadType.APPEND -> {
                Timber.d("Start APPEND")
                val nextPageKey = getNextPageKeyForLastItem(state)
                // TODO: 21.01.2021 The previousPage key should never be null but this should be fine (test with "asdasd")
                    ?: return MediatorResult.Success(endOfPaginationReached = true) // TODO: 21.01.2021 I skipped the exceptions from the codelabs but I'm not yet sure about that
                Timber.d("return APPEND with nextPageKey = $nextPageKey")
                nextPageKey
            }
        }

        return try {
            delay(1000)
            val apiResponse = newsApi.searchNews(searchQuery, page, state.config.pageSize)
            val serverSearchResults = apiResponse.articles
            val endOfPaginationReached = serverSearchResults.isEmpty()

            val bookmarkedArticles = newsArticleDao.getAllBookmarkedArticles().first()

            val searchResultArticles = serverSearchResults.map { serverSearchResultArticle ->
                val bookmarked = bookmarkedArticles.any { bookmarkedArticle ->
                    bookmarkedArticle.url == serverSearchResultArticle.url
                }

                NewsArticle(
                    title = serverSearchResultArticle.title,
                    url = serverSearchResultArticle.url,
                    urlToImage = serverSearchResultArticle.urlToImage,
                    isBookmarked = bookmarked,
                )
            }

            newsDb.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    newsArticleDao.clearSearchResultsForQuery(searchQuery)
                }

                val lastResultPosition = getQueryPositionForLastItem(state) ?: 0
                var position = lastResultPosition + 1

                val prevKey = if (page == NEWS_STARTING_PAGE_INDEX) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val searchResults = serverSearchResults.map { article ->
                    SearchResult(searchQuery, article.url, prevKey, nextKey, position++)
                }
                newsArticleDao.insertArticles(searchResultArticles)
                newsArticleDao.insertSearchResults(searchResults)
            }
            MediatorResult.Success(endOfPaginationReached)
        } catch (exception: IOException) {
            MediatorResult.Error(exception)
        } catch (exception: HttpException) {
            MediatorResult.Error(exception)
        }
    }

    private suspend fun getNextPageKeyForLastItem(state: PagingState<Int, NewsArticle>): Int? {
        return state.lastItemOrNull()?.let { article ->
            newsArticleDao.getSearchResult(article.url).nextPageKey
        }
    }

    private suspend fun getPreviousPageKeyForFirstItem(state: PagingState<Int, NewsArticle>): Int? {
        return state.firstItemOrNull()?.let { article ->
            newsArticleDao.getSearchResult(article.url).prevPageKey
        }
    }

    private suspend fun getNextPageKeyClosestToCurrentPosition(state: PagingState<Int, NewsArticle>): Int? {
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.url?.let { articleUrl ->
                newsArticleDao.getSearchResult(articleUrl).nextPageKey
            }
        }
    }

    private suspend fun getQueryPositionForLastItem(state: PagingState<Int, NewsArticle>): Int? {
        return state.lastItemOrNull()?.let { article ->
            newsArticleDao.getSearchResult(article.url).queryPosition
        }
    }
}