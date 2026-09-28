package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val taskType: String, // WHATSAPP, YOUTUBE, CALL, EMAIL, CODING_APP, APK_BUILD, THUMBNAIL, WEB_SEARCH, GENERAL
    val status: String = "QUEUED", // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    val currentStep: String = "Initialized",
    val target: String? = null,
    val details: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val verified: Boolean = false
)
