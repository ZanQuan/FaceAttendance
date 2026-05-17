package com.example.faceattendance.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {Student.class, Attendance.class, Teacher.class, ClassRoomEntity.class},
        version = 3,           // ← tăng version vì thêm bảng ClassRoom
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase instance;

    public abstract StudentDao studentDao();
    public abstract AttendanceDao attendanceDao();
    public abstract TeacherDao teacherDao();
    public abstract ClassRoomDao classRoomDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "face_attendance.db"
                    )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}