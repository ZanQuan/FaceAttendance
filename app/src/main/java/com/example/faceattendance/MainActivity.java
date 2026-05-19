package com.example.faceattendance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.faceattendance.camera.FaceOverlayView;
import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Attendance;
import com.example.faceattendance.database.Student;
import com.example.faceattendance.detector.FaceDetectorHelper;
import com.example.faceattendance.detector.FaceGeometricHelper;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG         = "MainActivity";
    private static final int    CAMERA_CODE = 100;

    private PreviewView     previewView;
    private FaceOverlayView faceOverlay;
    private TextView        tvStatus;
    private ImageCapture    imageCapture;

    private int     faceCount = 0;
    private String  currentClassCode;
    private String  currentClassName;
    private int     currentClassId = 0;
    private boolean isProcessing = false; // chống double-tap

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentClassCode = getIntent().getStringExtra(ClassSelectionActivity.EXTRA_CLASS_CODE);
        currentClassName = getIntent().getStringExtra(ClassSelectionActivity.EXTRA_CLASS_NAME);
        currentClassId   = getIntent().getIntExtra("classId", 0);

        previewView = findViewById(R.id.previewView);
        faceOverlay = findViewById(R.id.faceOverlay);
        tvStatus    = findViewById(R.id.tvStatus);

        if (currentClassName != null)
            tvStatus.setText("Lớp: " + currentClassName + " – Chưa phát hiện khuôn mặt");

        Button btnAttend   = findViewById(R.id.btnAttend);
        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnHistory  = findViewById(R.id.btnHistory);
        Button btnStudents = findViewById(R.id.btnStudents);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_CODE);
        } else {
            startCamera();
        }

        // ── Nút ĐIỂM DANH ──
        btnAttend.setOnClickListener(v -> {
            if (faceCount == 0) {
                Toast.makeText(this, "Không phát hiện khuôn mặt!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isProcessing) {
                Toast.makeText(this, "Đang xử lý...", Toast.LENGTH_SHORT).show();
                return;
            }
            captureAndRecognize();
        });

        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra("classId", currentClassId);
            startActivity(intent);
        });
        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        btnStudents.setOnClickListener(v -> {
            Intent intent = new Intent(this, StudentListActivity.class);
            intent.putExtra("classId", currentClassId);  // ← thêm
            startActivity(intent);
        });
    }

    // ─────────────────────────────────────────────
    // Camera (realtime preview + face overlay)
    // ─────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Realtime face detection → vẽ bounding box
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        new FaceDetectorHelper((faces, w, h) ->
                                runOnUiThread(() -> {
                                    faceCount = faces.size();
                                    faceOverlay.setFaces(faces, w, h);
                                    String prefix = currentClassName != null
                                            ? "Lớp: " + currentClassName + " – " : "";
                                    tvStatus.setText(faceCount == 0
                                            ? prefix + "Chưa phát hiện khuôn mặt"
                                            : prefix + "Phát hiện " + faceCount + " khuôn mặt ✓ — Nhấn Điểm danh");
                                })
                        )
                );

                // ImageCapture để chụp frame khi bấm điểm danh
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();

                CameraSelector cameraSelector;
                try {
                    cameraSelector = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                            ? CameraSelector.DEFAULT_FRONT_CAMERA
                            : CameraSelector.DEFAULT_BACK_CAMERA;
                } catch (Exception e) {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                }

                provider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview, analysis, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ─────────────────────────────────────────────
    // Điểm danh: Chụp → Trích đặc trưng → So khớp
    // ─────────────────────────────────────────────

    private void captureAndRecognize() {
        if (imageCapture == null) return;
        isProcessing = true;
        tvStatus.setText("Đang nhận diện...");

        // Lưu frame tạm
        File tmpDir  = new File(getCacheDir(), "tmp");
        if (!tmpDir.exists()) tmpDir.mkdirs();
        File tmpFile = new File(tmpDir, "attend_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions opts =
                new ImageCapture.OutputFileOptions.Builder(tmpFile).build();

        imageCapture.takePicture(opts,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) {
                        recognizeFromFile(tmpFile);
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        tvStatus.setText("Lỗi camera");
                        isProcessing = false;
                    }
                });
    }

    /** Đọc ảnh → trích đặc trưng → so khớp với DB */
    private void recognizeFromFile(File photoFile) {
        Bitmap original = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
        photoFile.delete();
        if (original == null) {
            tvStatus.setText("Không đọc được frame");
            isProcessing = false;
            return;
        }
        // Thử 4 góc xoay giống RegisterActivity
        tryRecognizeWithRotation(original, new int[]{0, 90, 270, 180}, 0);
    }

    private void tryRecognizeWithRotation(Bitmap original, int[] angles, int index) {
        if (index >= angles.length) {
            tvStatus.setText("Không nhận diện được — đứng thẳng trước camera");
            isProcessing = false;
            return;
        }

        int angle = angles[index];
        Bitmap rotated;
        if (angle == 0) {
            rotated = original;
        } else {
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(angle);
            rotated = Bitmap.createBitmap(original, 0, 0,
                    original.getWidth(), original.getHeight(), m, true);
        }

        FaceGeometricHelper.extractEmbedding(this, rotated,
                new FaceGeometricHelper.EmbeddingCallback() {
                    @Override
                    public void onSuccess(float[] queryEmbedding) {
                        new Thread(() -> matchAndRecord(queryEmbedding)).start();
                    }

                    @Override
                    public void onFailure(String reason) {
                        // Thử góc tiếp theo
                        tryRecognizeWithRotation(original, angles, index + 1);
                    }
                });
    }

    /** So khớp embedding với tất cả SV trong DB, ghi điểm danh nếu tìm thấy */
    private void matchAndRecord(float[] queryEmbedding) {
        AppDatabase db = AppDatabase.getInstance(this);
        List<Student> students = currentClassId > 0
                ? db.studentDao().getByClassId(currentClassId)
                : db.studentDao().getAll();

        if (students.isEmpty()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Chưa có sinh viên đăng ký!", Toast.LENGTH_SHORT).show();
                isProcessing = false;
            });
            return;
        }

        // Tìm SV có similarity cao nhất
        Student bestMatch = null;
        float   bestScore = -1f;

        for (Student s : students) {
            if (!s.hasEmbedding()) continue;

            float[] stored = s.getFaceEmbedding();
            float   score  = FaceGeometricHelper.cosineSimilarity(queryEmbedding, stored);
            Log.d(TAG, "So sánh với " + s.name + ": " + String.format("%.4f", score));

            if (score > bestScore) {
                bestScore = score;
                bestMatch = s;
            }
        }

        Log.d(TAG, "Kết quả tốt nhất: " + (bestMatch != null ? bestMatch.name : "null")
                + " | score=" + String.format("%.4f", bestScore)
                + " | threshold=" + FaceGeometricHelper.MATCH_THRESHOLD);

        // Kiểm tra ngưỡng
        if (bestMatch == null || bestScore < FaceGeometricHelper.MATCH_THRESHOLD) {
            final float fs = bestScore;
            runOnUiThread(() -> {
                tvStatus.setText(String.format(Locale.getDefault(),
                        "Không nhận ra ai (%.3f < %.3f)", fs, FaceGeometricHelper.MATCH_THRESHOLD));
                Toast.makeText(this,
                        "Khuôn mặt không khớp. Thử đứng thẳng, đủ sáng.", Toast.LENGTH_SHORT).show();
                isProcessing = false;
            });
            return;
        }

        final Student matched    = bestMatch;
        final float   matchScore = bestScore;

        // Kiểm tra đã điểm danh hôm nay chưa
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        for (Attendance existing : db.attendanceDao().getByDate(today)) {
            if (existing.studentId == matched.id) {
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            matched.name + " đã điểm danh hôm nay!", Toast.LENGTH_SHORT).show();
                    isProcessing = false;
                });
                return;
            }
        }

        // Ghi điểm danh
        Attendance a = new Attendance();
        a.studentId   = matched.id;
        a.studentName = matched.name;
        a.studentCode = matched.studentCode;
        a.timestamp   = System.currentTimeMillis();
        a.date        = today;
        a.time        = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        db.attendanceDao().insert(a);

        runOnUiThread(() -> {
            String classLabel = currentClassName != null ? " – " + currentClassName : "";
            String msg = String.format("✅ %s (%s) lúc %s%s | tương đồng %.1f%%",
                    matched.name, matched.studentCode, a.time, classLabel, matchScore * 100);

            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            tvStatus.setText("Đã điểm danh: " + matched.name +
                    String.format(Locale.getDefault(), " (%.1f%%)", matchScore * 100));
            isProcessing = false;
        });
    }

    // ─────────────────────────────────────────────
    // Permission
    // ─────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int code,
                                           @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == CAMERA_CODE && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}