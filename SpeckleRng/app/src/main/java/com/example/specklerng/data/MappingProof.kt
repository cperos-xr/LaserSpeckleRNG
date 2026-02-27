package com.example.specklerng.data

import kotlinx.serialization.Serializable

@Serializable
data class MappingProof(
    val algorithm: String,
    val word_index: Int,
    val word_value: Long,
    val limit: Long,
    val result: Int
)