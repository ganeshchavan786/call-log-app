package com.calllog.app.data.api

import com.calllog.app.data.api.models.*
import retrofit2.Response
import retrofit2.http.*

interface CallLogApiService {

    @POST("verify")
    suspend fun verify(
        @Body request: VerifyRequest
    ): Response<VerifyResponse>

    @POST("register-sim")
    suspend fun registerSim(
        @Header("Authorization") token: String,
        @Body request: RegisterSimRequest
    ): Response<ApiResponse>

    @POST("sync")
    suspend fun syncCallLogs(
        @Header("Authorization") token: String,
        @Body request: SyncRequest
    ): Response<SyncResponse>

    @GET("status")
    suspend fun getStatus(
        @Header("Authorization") token: String
    ): Response<ApiResponse>
}
