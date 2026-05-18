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
}