package com.michomuchomacho.rubato.github

import com.michomuchomacho.rubato.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GithubRetrofitClient(github: Github) {
    var retrofit: Retrofit

    init {
        retrofit = Retrofit.Builder()
                .baseUrl(github.url)
                .addConverterFactory(GsonConverterFactory.create())
                .client(getOkHttpClient())
                .build()
    }

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
                .addInterceptor(getHttpLoggingInterceptor())
                .build()
    }

    private fun getHttpLoggingInterceptor(): HttpLoggingInterceptor {
        val loggingInterceptor = HttpLoggingInterceptor()
        if (BuildConfig.DEBUG) {
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS)
        } else {
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE)
        }
        return loggingInterceptor
    }
}