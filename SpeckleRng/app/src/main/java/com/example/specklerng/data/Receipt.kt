package com.example.specklerng.data

import kotlinx.serialization.Serializable

@Serializable
data class Receipt(
    val value: Int,
    val min: Int,
    val max: Int,
    val receipt_id: String,
    val receipt_sha256: String,
    val mapping_proof: MappingProof,
    val urls: Map<String, String>
)