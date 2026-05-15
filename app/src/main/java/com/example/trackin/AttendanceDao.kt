package com.example.trackin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttendanceDao {

    @Insert
    suspend fun insertAttendance(attendance: AttendanceEntity)

    @Query("SELECT * FROM attendance_alerts ORDER BY id DESC")
    suspend fun getAllAttendance(): List<AttendanceEntity>
}
