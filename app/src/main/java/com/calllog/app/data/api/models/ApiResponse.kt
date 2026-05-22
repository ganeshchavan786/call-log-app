package com.calllog.app.data.api.models

data class ApiResponse(
    val success: Boolean,
    val message: String?,
    val error: String?,
    val data: Any?
)
