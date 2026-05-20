package com.example.faceattendance.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "classrooms")
public class ClassRoomEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String code;       // Mã lớp
    public String name;       // Tên lớp
    public String room;       // Phòng học
    public String time;       // Chuỗi hiển thị trên card
    public int    total;      // Sĩ số
    public long   createdAt;

    // ── 3 trường mới ──────────────────────────────
    @ColumnInfo(name = "sessions", defaultValue = "1")
    public int sessions = 1;          // 1 hoặc 2 buổi

    @NonNull
    @ColumnInfo(name = "start_time", defaultValue = "")
    public String startTime = "";     // Giờ bắt đầu, VD "07:30"

    @ColumnInfo(name = "grace_minutes", defaultValue = "15")
    public int graceMinutes = 15;     // Được trễ tối đa X phút
}