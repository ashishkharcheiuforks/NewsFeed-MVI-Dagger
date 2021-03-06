package com.android.myapplication.newsfeed.repository


import android.util.Log
import androidx.lifecycle.LiveData
import com.android.myapplication.newsfeed.api.NewsApi
import com.android.myapplication.newsfeed.api.data.ArticleNetwork
import com.android.myapplication.newsfeed.api.responses.HeadlinesResponse
import com.android.myapplication.newsfeed.di.main.MainScope
import com.android.myapplication.newsfeed.models.Article
import com.android.myapplication.newsfeed.persistence.ArticleDb
import com.android.myapplication.newsfeed.persistence.ArticlesDao
import com.android.myapplication.newsfeed.ui.DataState
import com.android.myapplication.newsfeed.ui.headlines.state.HeadlinesViewState
import com.android.myapplication.newsfeed.util.ApiSuccessResponse
import com.android.myapplication.newsfeed.util.GenericApiResponse
import com.android.myapplication.newsfeed.util.NetworkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import javax.inject.Inject

@MainScope
class HeadlinesRepository
@Inject
constructor(
    val newsApi: NewsApi,
    val articlesDao: ArticlesDao,
    val networkUtil: NetworkUtil
) : JobManager("HeadlinesRepository") {
    private val TAG: String = "AppDebug"

    fun getTopHeadlines(
        country: String = "",
        category: String = "general"
    ): LiveData<DataState<HeadlinesViewState>> {
        return object :
            NetworkBoundResource<HeadlinesResponse, List<ArticleDb>, HeadlinesViewState>(
                networkUtil.isConnectedToTheInternet(),
                true
            ) {
            override fun setJob(job: Job) {
                addJob("getTopHeadlines", job)
            }

            override suspend fun createCacheRequestAndReturn() {
                //NADA
            }

            override suspend fun handleApiSuccessResponse(response: ApiSuccessResponse<HeadlinesResponse>) {
                val articleList: ArrayList<Article> = ArrayList()
                //handleApiSuccessResponse is already called inside a coroutine with IO dispatcher
                val articleDbList: List<ArticleDb>? = loadFromCache()
                Log.d(TAG, "handleApiSuccessResponse: ${response.body.status}")
                val articleNetworkList :List<ArticleNetwork>? = response.body.articlesNetwork


                if(articleDbList.isNullOrEmpty()){
                    articleNetworkList?.forEach { articleNetwork->
                        articleList.add(
                            Article(
                                title = articleNetwork.title,
                                description = articleNetwork.description,
                                url = articleNetwork.url,
                                urlToImage = articleNetwork.urlToImage,
                                publishDate = articleNetwork.publishDate,
                                content = articleNetwork.content,
                                source = articleNetwork.source
                            )
                        )
                    }
                }

                //switch context because handleApiSuccessResponse is running inside IO dispatcher
                withContext(Dispatchers.Main){
                   onCompleteJob(DataState.data(HeadlinesViewState(HeadlinesViewState.HeadlineFields(articleList))))
                }


            }

            override fun createCall(): LiveData<GenericApiResponse<HeadlinesResponse>> {
                return newsApi.getTopHeadlines()
            }

            override fun loadFromCache(): List<ArticleDb>? {
                return articlesDao.getAllArticles()
            }

        }.asLiveData()


    }
}