package com.example.faceattendance.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "students")
public class Student {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;        // Họ tên
    public String studentCode; // MSSV
    public String photoPath;   // Đường dẫn ảnh
    public long createdAt;     // Thời gian đăng ký
}
