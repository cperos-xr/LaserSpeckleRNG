package com.example.specklerng.data

import kotlinx.serialization.Serializable

@Serializable
data class Status(
    val uptime_ms: Long,
    val server_uptime_ms: Long,
    val fps: Double,
    val diff_std: Double,
    val last_sha256_diff_prefix: String,
    val total_requests: Int,
    val total_receipts: Int,
    val entropy_ok: Boolean,
    val stuck: Boolean,
    val roi_size: Int,
    val mask_radius: Int
)
