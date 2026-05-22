package com.calllog.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://calllog.vrushaliinfotech.com/api/mobile/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)   // 30 → 10 sec: server down लवकर detect
        .readTimeout(60, TimeUnit.SECONDS)       // Response read — 500 records साठी वेळ लागतो
        .writeTimeout(30, TimeUnit.SECONDS)      // Request write
        .retryOnConnectionFailure(false)         // App level retry handle करतो
        .build()

    val service: CallLogApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CallLogApiService::class.java)
}
