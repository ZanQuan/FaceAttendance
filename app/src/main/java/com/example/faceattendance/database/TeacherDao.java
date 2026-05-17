package com.example.faceattendance.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface TeacherDao {

    @Insert
    void insert(Teacher teacher);

    @Query("SELECT * FROM teachers WHERE username = :username AND password = :password LIMIT 1")
    Teacher login(String username, String password);

    @Query("SELECT * FROM teachers WHERE username = :username LIMIT 1")
    Teacher findByUsername(String username);

    @Query("SELECT COUNT(*) FROM teachers")
    int count();
}