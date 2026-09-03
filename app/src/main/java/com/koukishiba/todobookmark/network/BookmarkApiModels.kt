package com.koukishiba.todobookmark.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BatchRequestItem(
    val url: String,
)

@Serializable
data class BatchRequestBody(
    val items: List<BatchRequestItem>,
    val status: String = "inbox",
    val source: String = "android",
)

@Serializable
enum class BatchResultStatus {
    @SerialName("created") CREATED,
    @SerialName("existing") EXISTING,
    @SerialName("invalid") INVALID,
}

@Serializable
data class BatchResultItem(
    val url: String,
    val status: BatchResultStatus,
    val id: String? = null,
)

@Serializable
data class BatchResponseBody(
    val results: List<BatchResultItem>,
)
