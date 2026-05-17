package com.example.faceattendance.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ClassRoomDao {

    @Insert
    void insert(ClassRoomEntity classRoom);

    @Delete
    void delete(ClassRoomEntity classRoom);

    @Query("SELECT * FROM classrooms ORDER BY createdAt DESC")
    List<ClassRoomEntity> getAll();

    @Query("SELECT COUNT(*) FROM classrooms")
    int count();
}