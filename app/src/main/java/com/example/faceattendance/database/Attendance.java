package com.example.faceattendance.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "attendance")
public class Attendance {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int studentId;

    public String studentName;

    public String studentCode;

    public String date;

    public String time;

    public long timestamp;
    public int classId;

    // Số phút đi trễ so với giờ bắt đầu lớp (0 = đúng giờ)
    @androidx.room.ColumnInfo(name = "late_minutes", defaultValue = "0")
    public int lateMinutes = 0;
}