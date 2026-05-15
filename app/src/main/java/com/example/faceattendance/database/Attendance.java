package com.example.faceattendance.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "attendance")
public class Attendance {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public int studentId;      // ID sinh viên
    public String studentName; // Tên sinh viên
    public String studentCode; // MSSV
    public long timestamp;     // Thời điểm điểm danh
    public String date;        // "2026-05-15"
    public String time;        // "14:30"
}

