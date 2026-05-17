package com.example.faceattendance.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "classrooms")
public class ClassRoomEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String code;   // Mã lớp
    public String name;   // Tên lớp
    public String room;   // Phòng học
    public String time;   // Thời gian
    public int    total;  // Sĩ số
    public long   createdAt;
}