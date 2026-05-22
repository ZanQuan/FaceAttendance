package com.example.faceattendance.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface StudentDao {

    @Insert
    long insert(Student student);

    @Update
    void update(Student student);

    @Delete
    void delete(Student student);

    @Query("SELECT * FROM students ORDER BY name ASC")
    List<Student> getAll();

    @Query("SELECT * FROM students WHERE id = :id")
    Student getById(int id);

    @Query("SELECT * FROM students WHERE studentCode = :code")
    Student getByCode(String code);
    @Query("SELECT * FROM students WHERE studentCode = :code AND classId = :classId LIMIT 1")
    Student getByCodeAndClass(String code, int classId);

    @Query("SELECT COUNT(*) FROM students")
    int getCount();

    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY name ASC")
    List<Student> getByClassId(int classId);

    @Query("DELETE FROM students WHERE classId = :classId")
    void deleteByClassId(int classId);
}
