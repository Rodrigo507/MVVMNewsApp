package com.codinginflow.mvvmnewsapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsArticleDao {

    @Query("SELECT * FROM news_articles")
    fun getAllArticles(): Flow<List<NewsArticle>>

    @Query("SELECT * FROM news_articles WHERE isBreakingNews = 1")
    fun getTopHeadlines(): Flow<List<NewsArticle>>

    @Query("SELECT * FROM news_articles WHERE isBookmarked = 1")
    fun getAllBookmarkedArticles(): Flow<List<NewsArticle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(articles: List<NewsArticle>)

    @Update
    suspend fun update(article: NewsArticle)

    @Query("UPDATE news_articles SET isBookmarked = 0")
    suspend fun deleteAllBookmarks()

    @Query("DELETE FROM news_articles WHERE isBookmarked = 0")
    suspend fun deleteNonBookmarkedArticles()

    @Query("DELETE FROM news_articles")
    suspend fun deleteAll()
}