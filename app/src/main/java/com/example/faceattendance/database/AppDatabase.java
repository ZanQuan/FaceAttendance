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
        version = 7,
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

    // v4 → v5: (giữ nguyên để không mất dữ liệu cũ)
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {}
    };

    // v5 → v6: thêm 2 cột embedding mới cho ảnh 2 và ảnh 3
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE students ADD COLUMN face_embedding_json_2 TEXT DEFAULT ''"
            );
            database.execSQL(
                    "ALTER TABLE students ADD COLUMN face_embedding_json_3 TEXT DEFAULT ''"
            );
        }
    };

    // v6 -> v7: lớp học thêm phần học 1 buổi hay 2 buổi (tick chọn)
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE classrooms ADD COLUMN sessions INTEGER NOT NULL DEFAULT 1"
            );
            database.execSQL(
                    "ALTER TABLE classrooms ADD COLUMN start_time TEXT NOT NULL DEFAULT ''"
            );
            database.execSQL(
                    "ALTER TABLE classrooms ADD COLUMN grace_minutes INTEGER NOT NULL DEFAULT 15"
            );
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
                            .addMigrations(
                                    MIGRATION_1_2,
                                    MIGRATION_2_3,
                                    MIGRATION_3_4,
                                    MIGRATION_4_5,
                                    MIGRATION_5_6,
                                    MIGRATION_6_7
                            )
                            // KHÔNG dùng fallbackToDestructiveMigration nữa
                            // vì đã có đủ migration → dữ liệu cũ không bị xóa
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}