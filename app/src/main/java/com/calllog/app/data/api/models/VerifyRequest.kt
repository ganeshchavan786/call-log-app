package com.calllog.app.data.api.models

data class VerifyRequest(
    val email: String,
    val password: String,
    val uniqueCode: String
)
