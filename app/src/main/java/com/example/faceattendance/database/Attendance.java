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
}