package com.example.faceattendance.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "teachers")
public class Teacher {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String fullName;   // Họ và tên giáo viên
    public String username;   // Tên đăng nhập
    public String password;   // Mật khẩu (nên hash trong thực tế)
    public String subject;    // Môn dạy
    public long createdAt;
}