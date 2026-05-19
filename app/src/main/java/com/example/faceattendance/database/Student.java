package com.example.faceattendance.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

import com.google.gson.Gson;

@Entity(tableName = "students")
public class Student {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String studentCode;
    public String photoPath;
    public long   createdAt;
    public int    classId;

    // ── Embedding #1: nhìn thẳng ──
    public String faceEmbeddingJson;

    // ── Embedding #2: nghiêng trái (cột mới) ──
    @ColumnInfo(name = "face_embedding_json_2")
    public String faceEmbeddingJson2;

    // ── Embedding #3: nghiêng phải (cột mới) ──
    @ColumnInfo(name = "face_embedding_json_3")
    public String faceEmbeddingJson3;

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private static final Gson GSON = new Gson();

    public float[] getFaceEmbedding() {
        if (faceEmbeddingJson == null || faceEmbeddingJson.isEmpty()) return null;
        return GSON.fromJson(faceEmbeddingJson, float[].class);
    }

    public void setFaceEmbedding(float[] embedding) {
        this.faceEmbeddingJson = embedding == null ? null : GSON.toJson(embedding);
    }

    // ── Embedding 2 ──
    public float[] getFaceEmbedding2() {
        if (faceEmbeddingJson2 == null || faceEmbeddingJson2.isEmpty()) return null;
        return GSON.fromJson(faceEmbeddingJson2, float[].class);
    }

    public void setFaceEmbedding2(float[] embedding) {
        this.faceEmbeddingJson2 = embedding == null ? null : GSON.toJson(embedding);
    }

    // ── Embedding 3 ──
    public float[] getFaceEmbedding3() {
        if (faceEmbeddingJson3 == null || faceEmbeddingJson3.isEmpty()) return null;
        return GSON.fromJson(faceEmbeddingJson3, float[].class);
    }

    public void setFaceEmbedding3(float[] embedding) {
        this.faceEmbeddingJson3 = embedding == null ? null : GSON.toJson(embedding);
    }

    /** Kiểm tra đã có ít nhất embedding #1 chưa */
    public boolean hasEmbedding() {
        return faceEmbeddingJson != null && !faceEmbeddingJson.isEmpty();
    }
}