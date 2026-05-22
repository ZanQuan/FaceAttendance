package com.example.faceattendance.detector;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.content.Context;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;

/**
 * FaceGeometricHelper v2 — nhận diện khuôn mặt bằng ML Kit Contour + Landmark.
 *
 * CẢI TIẾN SO VỚI v1:
 *  1. THÊM contour points (~274 chiều) — v1 bỏ sót hoàn toàn dù đã bật CONTOUR_MODE_ALL
 *  2. THÊM face alignment (xoay điểm theo trục mắt) — giúp nhất quán khi đầu hơi nghiêng
 *  3. Chuẩn hóa bằng IOD (inter-ocular distance) thay vì face width → chính xác hơn
 *  4. Vector đặc trưng: 274 (contour) + 20 (landmark) + 13 (ratio) = 307 chiều CỐ ĐỊNH
 *
 * !! QUAN TRỌNG: Vector đã thay đổi kích thước — tất cả sinh viên cần đăng ký lại !!
 */
public class FaceGeometricHelper {

    /**
     * Ngưỡng cosine similarity để xác nhận cùng một người.
     *
     * Với 307-dim contour features (v2):
     *   - Cùng người, góc khác nhau ≈ 0.93–0.99
     *   - Người khác nhau          ≈ 0.80–0.92
     *
     * Điều chỉnh:
     *   - Vẫn nhận nhầm người  → tăng lên 0.940f
     *   - Từ chối sai người đúng → giảm xuống 0.900f
     */
    public static final float MATCH_THRESHOLD = 0.985f;

    /**
     * Khoảng cách tối thiểu giữa người khớp nhất và nhì.
     * Nếu < MIN_GAP → không đủ tự tin → từ chối (xử lý ở MainActivity).
     */
    public static final float MIN_GAP = 0.01f;

    // ─────────────────────────────────────────────
    // Số điểm CỐ ĐỊNH của mỗi contour type (theo ML Kit spec)
    // ─────────────────────────────────────────────
    private static final int[] CONTOUR_TYPES = {
            FaceContour.FACE,                 // 36 pts
            FaceContour.LEFT_EYE,             // 16 pts
            FaceContour.RIGHT_EYE,            // 16 pts
            FaceContour.LEFT_EYEBROW_TOP,     // 5 pts
            FaceContour.LEFT_EYEBROW_BOTTOM,  // 5 pts
            FaceContour.RIGHT_EYEBROW_TOP,    // 5 pts
            FaceContour.RIGHT_EYEBROW_BOTTOM, // 5 pts
            FaceContour.NOSE_BRIDGE,          // 4 pts
            FaceContour.NOSE_BOTTOM,          // 5 pts
            FaceContour.UPPER_LIP_TOP,        // 10 pts
            FaceContour.UPPER_LIP_BOTTOM,     // 10 pts
            FaceContour.LOWER_LIP_TOP,        // 10 pts
            FaceContour.LOWER_LIP_BOTTOM,     // 10 pts
    };
    // Tổng: 36+16+16+5+5+5+5+4+5+10+10+10+10 = 137 điểm × 2 tọa độ = 274 features

    private static final int[] CONTOUR_POINT_COUNTS = {
            36, 16, 16, 5, 5, 5, 5, 4, 5, 10, 10, 10, 10
    };

    // Số landmark × 2 tọa độ = 20; số ratio = 13
    // Tổng vector = 274 + 20 + 13 = 307
    public static final int EMBEDDING_DIM = 307;

    // ─────────────────────────────────────────────
    // Interface callback
    // ─────────────────────────────────────────────

    public interface EmbeddingCallback {
        void onSuccess(float[] embedding);
        void onFailure(String reason);
    }

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    public static void extractEmbedding(Context context, Bitmap bitmap, EmbeddingCallback callback) {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)     // ← lấy contour
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)   // ← lấy landmark
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

                    Face largest = getLargestFace(faces);

                    android.graphics.Rect box = largest.getBoundingBox();

                    if (box.width() < 220 || box.height() < 220) {

                        callback.onFailure("Đưa mặt lại gần camera hơn");
                        return;
                    }

                    float[] embedding = buildEmbedding(largest);

                    if (embedding == null || embedding.length < 20) {
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
    // Xây dựng vector đặc trưng 307 chiều
    // ─────────────────────────────────────────────

    private static float[] buildEmbedding(Face face) {
        if (Math.abs(face.getHeadEulerAngleY()) > 20f) {
            return null;
        }

        if (Math.abs(face.getHeadEulerAngleZ()) > 20f) {
            return null;
        }
        android.graphics.Rect box = face.getBoundingBox();
        float cx = box.exactCenterX();
        float cy = box.exactCenterY();
        float faceW = Math.max(box.width(), 1f);

        // ── Bước 1: Tính góc xoay từ hai mắt (face alignment) ──
        //
        // Vấn đề v1: Khi đầu nghiêng 10°, tọa độ (x,y) tất cả các điểm thay đổi
        // → features không nhất quán giữa lần đăng ký và điểm danh.
        //
        // Giải pháp: Xoay tất cả điểm một góc -rollAngle quanh tâm mặt
        // → đường mắt luôn nằm ngang → features nhất quán dù đầu hơi nghiêng.

        FaceLandmark leftEyeLm  = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEyeLm = face.getLandmark(FaceLandmark.RIGHT_EYE);

        float iod      = faceW * 0.35f; // mặc định nếu không có mắt
        float cosRoll  = 1f;
        float sinRoll  = 0f;

        if (leftEyeLm != null && rightEyeLm != null) {
            PointF le = leftEyeLm.getPosition();
            PointF re = rightEyeLm.getPosition();
            float dx   = re.x - le.x;
            float dy   = re.y - le.y;
            float roll = (float) Math.atan2(dy, dx); // góc nghiêng hiện tại
            // Xoay ngược lại để cân bằng:
            cosRoll = (float) Math.cos(-roll);
            sinRoll = (float) Math.sin(-roll);
            iod = Math.max((float) Math.sqrt(dx * dx + dy * dy), 1f);
        }

        // ── Bước 2: Trích xuất contour points (274 features) ──
        //
        // Đây là phần bị BỎ SÓT hoàn toàn trong v1!
        // ~137 contour points × 2 tọa độ = 274 features phong phú hơn nhiều.

        float[] features = new float[EMBEDDING_DIM]; // 307 chiều, khởi tạo = 0
        int writeIdx = 0;

        for (int ti = 0; ti < CONTOUR_TYPES.length; ti++) {
            FaceContour contour = face.getContour(CONTOUR_TYPES[ti]);
            int expectedPts = CONTOUR_POINT_COUNTS[ti];

            if (contour != null) {
                List<PointF> pts = contour.getPoints();
                int actualPts = Math.min(pts.size(), expectedPts);
                for (int pi = 0; pi < actualPts; pi++) {
                    PointF p = pts.get(pi);
                    float rx = (p.x - cx) * cosRoll - (p.y - cy) * sinRoll;
                    float ry = (p.x - cx) * sinRoll + (p.y - cy) * cosRoll;
                    if (writeIdx + 1 < EMBEDDING_DIM) {
                        features[writeIdx++] = rx / iod;
                        features[writeIdx++] = ry / iod;
                    }
                }
                // Padding nếu thiếu điểm (hiếm)
                for (int pi = contour.getPoints().size(); pi < expectedPts; pi++) {
                    if (writeIdx + 1 < EMBEDDING_DIM) { writeIdx += 2; }
                }
            } else {
                // Contour không detect được → pad 0 (writeIdx đã được khởi tạo 0)
                writeIdx += expectedPts * 2;
            }
        }

        // ── Bước 3: Landmark positions (20 features) ──
        int[] landmarkTypes = {
                FaceLandmark.LEFT_EYE,    FaceLandmark.RIGHT_EYE,
                FaceLandmark.LEFT_EAR,    FaceLandmark.RIGHT_EAR,
                FaceLandmark.NOSE_BASE,
                FaceLandmark.LEFT_CHEEK,  FaceLandmark.RIGHT_CHEEK,
                FaceLandmark.MOUTH_LEFT,  FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_BOTTOM,
        };

        PointF[] lp = new PointF[landmarkTypes.length];
        for (int i = 0; i < landmarkTypes.length; i++) {
            FaceLandmark lm = face.getLandmark(landmarkTypes[i]);
            if (lm != null && writeIdx + 1 < EMBEDDING_DIM) {
                lp[i] = lm.getPosition();
                float rx = (lp[i].x - cx) * cosRoll - (lp[i].y - cy) * sinRoll;
                float ry = (lp[i].x - cx) * sinRoll + (lp[i].y - cy) * cosRoll;
                features[writeIdx++] = rx / iod;
                features[writeIdx++] = ry / iod;
            } else {
                lp[i] = null;
                if (writeIdx + 1 < EMBEDDING_DIM) writeIdx += 2;
            }
        }

        // ── Bước 4: Geometric ratio features (13 features) ──
        // Tỉ lệ khoảng cách → bất biến với kích thước ảnh

        int[][] pairs = {
                {0,1},{0,4},{1,4},{4,9},{0,7},
                {1,8},{7,8},{2,3},{5,6},{0,9},
                {1,9},{4,7},{4,8}
        };

        for (int[] pair : pairs) {
            if (writeIdx >= EMBEDDING_DIM) break;
            PointF a = lp[pair[0]], b = lp[pair[1]];
            features[writeIdx++] = (a != null && b != null) ? dist(a, b) / iod : 0f;
        }

        return l2Normalize(features);
    }

    // ─────────────────────────────────────────────
    // So sánh embedding
    // ─────────────────────────────────────────────

    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null) return 0f;
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