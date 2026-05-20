package com.example.faceattendance.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AttendanceDao {

    @Insert
    long insert(Attendance attendance);

    @Query("SELECT * FROM attendance ORDER BY timestamp DESC")
    List<Attendance> getAll();

    @Query("SELECT * FROM attendance WHERE date = :date ORDER BY timestamp DESC")
    List<Attendance> getByDate(String date);

    @Query("SELECT * FROM attendance WHERE studentId = :studentId")
    List<Attendance> getByStudent(int studentId);

    @Query("DELETE FROM attendance")
    void deleteAll();

    @Query("DELETE FROM attendance WHERE classId = :classId")
    void deleteByClassId(int classId);

    // Lấy danh sách sinh viên đi trễ (lateMinutes > 0)
    @Query("SELECT * FROM attendance WHERE late_minutes > 0 ORDER BY timestamp DESC")
    List<Attendance> getLateStudents();

    // Lấy danh sách đi trễ theo lớp
    @Query("SELECT * FROM attendance WHERE late_minutes > 0 AND classId = :classId ORDER BY timestamp DESC")
    List<Attendance> getLateStudentsByClass(int classId);

    // Lấy toàn bộ điểm danh theo lớp
    @Query("SELECT * FROM attendance WHERE classId = :classId ORDER BY timestamp DESC")
    List<Attendance> getByClassId(int classId);
}