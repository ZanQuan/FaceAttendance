package com.example.faceattendance.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {Student.class, Attendance.class, ClassRoomEntity.class, Teacher.class},
        version = 5,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract StudentDao    studentDao();
    public abstract AttendanceDao attendanceDao();
    public abstract ClassRoomDao  classRoomDao();
    public abstract TeacherDao    teacherDao();

    private static volatile AppDatabase INSTANCE;

    // v1 → v2: thêm cột faceEmbeddingJson
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE students ADD COLUMN faceEmbeddingJson TEXT");
        }
    };

    // v2 → v3: không đổi schema
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {}
    };

    // v3 → v4: đảm bảo cột faceEmbeddingJson tồn tại
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE students ADD COLUMN faceEmbeddingJson TEXT");
            } catch (Exception e) {
                // Cột đã tồn tại — bỏ qua
            }
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "face_attendance.db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration() // xóa DB nếu vẫn còn xung đột
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}