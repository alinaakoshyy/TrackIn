package com.example.trackin

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_alerts")
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val studentName: String,
    val rollNumber: String,
    val teacherPhone: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val smsStatus: String
)
