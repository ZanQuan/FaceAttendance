package com.example.faceattendance.detector;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.Uri;
import android.content.Context;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;

/**
 * FaceGeometricHelper — nhận diện khuôn mặt hoàn toàn bằng ML Kit.
 *
 * KHÔNG cần tải thêm bất kỳ file model nào.
 *
 * Nguyên lý:
 *  1. Dùng ML Kit CONTOUR_MODE + LANDMARK_MODE để lấy ~100 điểm đặc trưng hình học
 *     (viền mặt, mắt, mũi, miệng, lông mày...)
 *  2. Chuẩn hóa các điểm về hệ tọa độ độc lập với kích thước và vị trí khuôn mặt
 *  3. Tạo vector đặc trưng (embedding) → lưu vào DB khi đăng ký
 *  4. Khi điểm danh: so sánh bằng cosine similarity
 */
public class FaceGeometricHelper {

    /**
     * Ngưỡng cosine similarity để xác nhận cùng một người.
     * Geometric features cần ngưỡng cao hơn neural embedding (~0.97 so với ~0.65).
     * Tăng lên 0.975 nếu bị nhận nhầm người; giảm xuống 0.960 nếu bị từ chối sai.
     */
    public static final float MATCH_THRESHOLD = 0.970f;

    // Kích thước vector đặc trưng (sẽ phụ thuộc vào số contour điểm ML Kit trả về)
    // Dùng List để linh hoạt, sau đó convert sang float[]

    // ─────────────────────────────────────────────
    // Interface callback (vì ML Kit dùng async)
    // ─────────────────────────────────────────────

    public interface EmbeddingCallback {
        /** Gọi khi trích xuất thành công */
        void onSuccess(float[] embedding);
        /** Gọi khi không tìm thấy mặt hoặc có lỗi */
        void onFailure(String reason);
    }

    // ─────────────────────────────────────────────
    // Public API — trích xuất embedding từ Bitmap
    // ─────────────────────────────────────────────

    /**
     * Trích xuất vector đặc trưng từ một Bitmap chứa khuôn mặt.
     * Kết quả trả về qua callback (chạy trên thread gọi ML Kit).
     *
     * @param context  Context của Activity
     * @param bitmap   Ảnh chứa khuôn mặt (toàn ảnh hoặc đã crop)
     * @param callback EmbeddingCallback để nhận kết quả
     */
    public static void extractEmbedding(Context context, Bitmap bitmap, EmbeddingCallback callback) {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)       // ← key: lấy contour
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)     // ← key: lấy landmark
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.1f)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    detector.close();

                    if (faces == null || faces.isEmpty()) {
                        callback.onFailure("Không phát hiện khuôn mặt");
                        return;
                    }

                    // Lấy khuôn mặt lớn nhất (gần camera nhất)
                    Face largest = getLargestFace(faces);
                    float[] embedding = buildEmbedding(largest);

                    if (embedding == null || embedding.length < 10) {
                        callback.onFailure("Không đủ điểm đặc trưng (giữ mặt thẳng, đủ ánh sáng)");
                        return;
                    }

                    callback.onSuccess(embedding);
                })
                .addOnFailureListener(e -> {
                    detector.close();
                    callback.onFailure("Lỗi ML Kit: " + e.getMessage());
                });
    }

    // ─────────────────────────────────────────────
    // Xây dựng vector đặc trưng từ Face object
    // ─────────────────────────────────────────────

    /**
     * Tạo vector đặc trưng hình học từ contour + landmark + ratio features.
     *
     * Vector gồm 3 phần:
     *  A) Normalized contour points    — hình dạng các đường viền
     *  B) Normalized landmark points   — vị trí các điểm mốc chính
     *  C) Geometric ratio features     — tỉ lệ khoảng cách → bất biến với ảnh sáng/màu sắc
     */
    private static float[] buildEmbedding(Face face) {
        android.graphics.Rect box = face.getBoundingBox();
        float faceW = Math.max(box.width(),  1f);
        float faceH = Math.max(box.height(), 1f);
        float cx    = box.exactCenterX();
        float cy    = box.exactCenterY();

        // ── Phần B: Landmark positions (10 điểm × 2 = 20 features, LUÔN CỐ ĐỊNH) ──
        int[] landmarkTypes = {
                FaceLandmark.LEFT_EYE,
                FaceLandmark.RIGHT_EYE,
                FaceLandmark.LEFT_EAR,
                FaceLandmark.RIGHT_EAR,
                FaceLandmark.NOSE_BASE,
                FaceLandmark.LEFT_CHEEK,
                FaceLandmark.RIGHT_CHEEK,
                FaceLandmark.MOUTH_LEFT,
                FaceLandmark.MOUTH_RIGHT,
                FaceLandmark.MOUTH_BOTTOM,
        };

        PointF[] lp = new PointF[landmarkTypes.length];
        // Khai báo features với kích thước CỐ ĐỊNH ngay từ đầu
        float[] features = new float[20 + 13]; // B=20, C=13

        for (int i = 0; i < landmarkTypes.length; i++) {
            FaceLandmark lm = face.getLandmark(landmarkTypes[i]);
            if (lm != null) {
                lp[i] = lm.getPosition();
                features[i * 2]     = (lm.getPosition().x - cx) / faceW;
                features[i * 2 + 1] = (lm.getPosition().y - cy) / faceH;
            } else {
                lp[i] = null;
                features[i * 2]     = 0f;
                features[i * 2 + 1] = 0f;
            }
        }

        // ── Phần C: Geometric ratio features (13 ratios, LUÔN CỐ ĐỊNH) ──
        int[][] pairs = {
                {0,1},{0,4},{1,4},{4,9},{0,7},
                {1,8},{7,8},{2,3},{5,6},{0,9},
                {1,9},{4,7},{4,8}
        };

        float iod = 1f;
        if (lp[0] != null && lp[1] != null) {
            iod = dist(lp[0], lp[1]);
            if (iod < 1f) iod = 1f;
        }

        int offset = 20; // bắt đầu sau phần B
        for (int i = 0; i < pairs.length; i++) {
            PointF a = lp[pairs[i][0]];
            PointF b = lp[pairs[i][1]];
            features[offset + i] = (a != null && b != null) ? dist(a, b) / iod : 0f;
        }

        return l2Normalize(features);
    }

    // ─────────────────────────────────────────────
    // So sánh embedding
    // ─────────────────────────────────────────────

    /**
     * Tính cosine similarity giữa hai vector.
     * @return [0, 1] — càng gần 1 càng giống nhau
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null) return 0f;

        // Nếu khác chiều dài (hiếm gặp, xảy ra khi ML Kit trả về số contour khác nhau)
        // → so sánh phần chung, bỏ qua phần dư
        int len = Math.min(a.length, b.length);

        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < len; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0f;
        return (float)(dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /** Kiểm tra hai embedding có thuộc cùng một người không */
    public static boolean isSamePerson(float[] a, float[] b) {
        return cosineSimilarity(a, b) >= MATCH_THRESHOLD;
    }

    // ─────────────────────────────────────────────
    // Math helpers
    // ─────────────────────────────────────────────

    private static float dist(PointF a, PointF b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float[] l2Normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm < 1e-9) return v;
        float[] result = new float[v.length];
        for (int i = 0; i < v.length; i++) result[i] = (float)(v[i] / norm);
        return result;
    }

    private static Face getLargestFace(List<Face> faces) {
        Face largest = faces.get(0);
        int maxArea  = largest.getBoundingBox().width() * largest.getBoundingBox().height();
        for (Face f : faces) {
            int area = f.getBoundingBox().width() * f.getBoundingBox().height();
            if (area > maxArea) { maxArea = area; largest = f; }
        }
        return largest;
    }
}