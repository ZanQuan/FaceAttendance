package com.example.faceattendance.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;

@Entity(tableName = "students")
public class Student {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;          // Họ tên
    public String studentCode;   // MSSV
    public String photoPath;     // Đường dẫn ảnh
    public long   createdAt;     // Thời gian đăng ký

    /**
     * Face embedding vector (128 chiều) được serialize sang JSON String.
     * Dùng getFaceEmbedding() / setFaceEmbedding() để đọc/ghi.
     */
    public String faceEmbeddingJson;

    // ─────────────────────────────────────────────
    // Helpers serialize/deserialize embedding
    // ─────────────────────────────────────────────

    private static final Gson GSON = new Gson();

    /** Lấy embedding dưới dạng float[] (null nếu chưa có) */
    public float[] getFaceEmbedding() {
        if (faceEmbeddingJson == null || faceEmbeddingJson.isEmpty()) return null;
        return GSON.fromJson(faceEmbeddingJson, float[].class);
    }

    /** Lưu embedding float[] vào field JSON */
    public void setFaceEmbedding(float[] embedding) {
        this.faceEmbeddingJson = embedding == null ? null : GSON.toJson(embedding);
    }

    /** Kiểm tra sinh viên này đã có embedding chưa */
    public boolean hasEmbedding() {
        return faceEmbeddingJson != null && !faceEmbeddingJson.isEmpty();
    }
}