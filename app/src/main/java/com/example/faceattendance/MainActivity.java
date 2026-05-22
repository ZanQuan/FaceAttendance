package com.example.faceattendance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_CODE = 100;

    private PreviewView previewView;
    private FaceOverlayView faceOverlay;
    private TextView tvStatus;

    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private boolean cameraStarted = false;
    private ProcessCameraProvider cameraProvider;

    private final ExecutorService cameraExecutor =
            Executors.newSingleThreadExecutor();

    private int faceCount = 0;

    private String currentClassCode;
    private String currentClassName;

    private int currentClassId = 0;

    private String classStartTime = "";

    private int classGraceMinutes = 15;

    private boolean isProcessing = false;

    // =========================================================
    // Lifecycle
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        currentClassCode =
                getIntent().getStringExtra(
                        ClassSelectionActivity.EXTRA_CLASS_CODE
                );

        currentClassName =
                getIntent().getStringExtra(
                        ClassSelectionActivity.EXTRA_CLASS_NAME
                );

        currentClassId =
                getIntent().getIntExtra("classId", 0);

        loadClassInfo();

        previewView = findViewById(R.id.previewView);
        faceOverlay = findViewById(R.id.faceOverlay);
        tvStatus = findViewById(R.id.tvStatus);

        if (currentClassName != null) {

            tvStatus.setText(
                    "Lớp: " +
                            currentClassName +
                            " – Chưa phát hiện khuôn mặt"
            );
        }

        Button btnAttend = findViewById(R.id.btnAttend);
        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnHistory = findViewById(R.id.btnHistory);
        Button btnStudents = findViewById(R.id.btnStudents);

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_CODE
            );

        } else {

            startCamera();
        }

        // =====================================================
        // Điểm danh
        // =====================================================

        btnAttend.setOnClickListener(v -> {

            if (faceCount == 0) {

                Toast.makeText(
                        this,
                        "Không phát hiện khuôn mặt!",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (isProcessing) {

                Toast.makeText(
                        this,
                        "Đang xử lý...",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            captureAndRecognize();
        });

        // =====================================================
        // Đăng ký
        // =====================================================

        btnRegister.setOnClickListener(v -> {

            Intent intent =
                    new Intent(this, RegisterActivity.class);

            intent.putExtra("classId", currentClassId);

            startActivity(intent);
        });

        // =====================================================
        // Lịch sử
        // =====================================================

        btnHistory.setOnClickListener(v -> {

            Intent intent =
                    new Intent(this, HistoryActivity.class);

            intent.putExtra("classId", currentClassId);
            intent.putExtra("className", currentClassName);

            startActivity(intent);
        });

        // =====================================================
        // Danh sách SV
        // =====================================================

        btnStudents.setOnClickListener(v -> {

            Intent intent =
                    new Intent(this, StudentListActivity.class);

            intent.putExtra("classId", currentClassId);

            startActivity(intent);
        });
    }

    // =========================================================
    // Load thông tin lớp
    // =========================================================

    private void loadClassInfo() {

        int finalClassId = currentClassId;

        new Thread(() -> {

            if (finalClassId > 0) {

                com.example.faceattendance.database.ClassRoomEntity cls =
                        AppDatabase
                                .getInstance(this)
                                .classRoomDao()
                                .getById(finalClassId);

                if (cls != null) {

                    classStartTime =
                            cls.startTime != null
                                    ? cls.startTime
                                    : "";

                    classGraceMinutes =
                            cls.graceMinutes;
                }
            }

        }).start();
    }

    // =========================================================
    // Camera
    // =========================================================

    private void startCamera() {

        if (cameraStarted) return;

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {

            try {

                cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();

                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build();

                imageAnalysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        new FaceDetectorHelper((faces, w, h) ->

                                runOnUiThread(() -> {

                                    faceCount = faces.size();

                                    if (!isProcessing) {

                                        faceOverlay.setFaces(faces, w, h);

                                        String prefix =
                                                currentClassName != null
                                                        ? "Lớp: " + currentClassName + " – "
                                                        : "";

                                        if (faceCount == 0) {

                                            tvStatus.setText(
                                                    prefix + "Chưa phát hiện khuôn mặt"
                                            );

                                        } else {

                                            tvStatus.setText(
                                                    prefix +
                                                            "Phát hiện " +
                                                            faceCount +
                                                            " khuôn mặt ✓ — Nhấn Điểm danh"
                                            );
                                        }
                                    }
                                })
                        ));

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(
                                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .build();

                cameraProvider.unbindAll();

                CameraSelector selector;

                try {

                    selector =
                            cameraProvider.hasCamera(
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                            )
                                    ? CameraSelector.DEFAULT_FRONT_CAMERA
                                    : CameraSelector.DEFAULT_BACK_CAMERA;

                } catch (Exception e) {

                    selector = CameraSelector.DEFAULT_BACK_CAMERA;
                }

                previewView.setImplementationMode(
                        PreviewView.ImplementationMode.COMPATIBLE
                );

                cameraProvider.bindToLifecycle(
                        this,
                        selector,
                        preview,
                        imageAnalysis,
                        imageCapture
                );

                cameraStarted = true;

            } catch (Exception e) {

                Log.e(TAG, "startCamera error", e);
            }

        }, ContextCompat.getMainExecutor(this));
    }

    private void stopCamera() {

        try {

            if (cameraProvider != null) {

                cameraProvider.unbindAll();
            }

            cameraStarted = false;

        } catch (Exception e) {

            Log.e(TAG, "stopCamera error", e);
        }
    }

    // =========================================================
    // Reset camera state
    // =========================================================

    private void resetCameraState() {

        runOnUiThread(() -> {

            isProcessing = false;

            faceCount = 0;

            faceOverlay.clearFaces();

            String prefix =
                    currentClassName != null
                            ? "Lớp: " +
                            currentClassName +
                            " – "
                            : "";

            tvStatus.setText(
                    prefix + "Chưa phát hiện khuôn mặt"
            );
        });
    }

    // =========================================================
    // Capture & recognize
    // =========================================================

    private void captureAndRecognize() {

        if (imageCapture == null) return;

        isProcessing = true;

        tvStatus.setText("Đang nhận diện...");

        faceOverlay.clearFaces();

        File tmpDir =
                new File(getCacheDir(), "tmp");

        if (!tmpDir.exists()) {

            tmpDir.mkdirs();
        }

        File tmpFile =
                new File(
                        tmpDir,
                        "attend_" +
                                System.currentTimeMillis() +
                                ".jpg"
                );

        ImageCapture.OutputFileOptions opts =
                new ImageCapture.OutputFileOptions.Builder(
                        tmpFile
                ).build();

        imageCapture.takePicture(
                opts,
                ContextCompat.getMainExecutor(this),

                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(
                            @NonNull
                            ImageCapture.OutputFileResults result
                    ) {

                        recognizeFromFile(tmpFile);
                    }

                    @Override
                    public void onError(
                            @NonNull
                            ImageCaptureException e
                    ) {

                        resetCameraState();

                        tvStatus.setText("Lỗi camera");
                    }
                }
        );
    }

    // =========================================================
    // Recognize from file
    // =========================================================

    private void recognizeFromFile(File photoFile) {

        Bitmap original =
                BitmapFactory.decodeFile(
                        photoFile.getAbsolutePath()
                );

        photoFile.delete();

        if (original == null) {

            resetCameraState();

            tvStatus.setText(
                    "Không nhận diện được — đứng thẳng trước camera"
            );

            return;
        }

        tryRecognizeWithRotation(
                original,
                new int[]{0, 90, 270, 180},
                0
        );
    }

    // =========================================================
    // Rotate thử nhiều góc
    // =========================================================

    private void tryRecognizeWithRotation(
            Bitmap original,
            int[] angles,
            int index
    ) {

        if (index >= angles.length) {

            resetCameraState();

            tvStatus.setText(
                    "Không nhận diện được — đứng thẳng trước camera"
            );

            return;
        }

        int angle = angles[index];

        Bitmap rotated;

        if (angle == 0) {

            rotated = original;

        } else {

            Matrix m = new Matrix();

            m.postRotate(angle);

            rotated = Bitmap.createBitmap(
                    original,
                    0,
                    0,
                    original.getWidth(),
                    original.getHeight(),
                    m,
                    true
            );
        }

        FaceGeometricHelper.extractEmbedding(
                this,
                rotated,

                new FaceGeometricHelper.EmbeddingCallback() {

                    @Override
                    public void onSuccess(float[] queryEmbedding) {

                        new Thread(() ->
                                matchAndRecord(queryEmbedding)
                        ).start();
                    }

                    @Override
                    public void onFailure(String reason) {

                        tryRecognizeWithRotation(
                                original,
                                angles,
                                index + 1
                        );
                    }
                }
        );
    }

    // =========================================================
    // Match & record attendance
    // =========================================================

    private void matchAndRecord(float[] queryEmbedding) {

        AppDatabase db =
                AppDatabase.getInstance(this);

        List<Student> students =
                currentClassId > 0
                        ? db.studentDao().getByClassId(currentClassId)
                        : db.studentDao().getAll();

        if (students.isEmpty()) {

            runOnUiThread(() -> {

                Toast.makeText(
                        this,
                        "Chưa có sinh viên đăng ký!",
                        Toast.LENGTH_SHORT
                ).show();

                resetCameraState();
            });

            return;
        }

        Student bestMatch = null;

        float bestScore = -1f;

        float secondBestScore = -1f;

        // =====================================================
        // So khớp
        // =====================================================

        for (Student s : students) {

            if (!s.hasEmbedding()) continue;

            float bestPersonScore = 0f;

            float score1 =
                    FaceGeometricHelper.cosineSimilarity(
                            queryEmbedding,
                            s.getFaceEmbedding()
                    );

            if (score1 > bestPersonScore) {
                bestPersonScore = score1;
            }

            float[] emb2 =
                    s.getFaceEmbedding2();

            if (emb2 != null) {

                float score2 =
                        FaceGeometricHelper.cosineSimilarity(
                                queryEmbedding,
                                emb2
                        );

                if (score2 > bestPersonScore) {
                    bestPersonScore = score2;
                }
            }

            float[] emb3 =
                    s.getFaceEmbedding3();

            if (emb3 != null) {

                float score3 =
                        FaceGeometricHelper.cosineSimilarity(
                                queryEmbedding,
                                emb3
                        );

                if (score3 > bestPersonScore) {
                    bestPersonScore = score3;
                }
            }

            float score = bestPersonScore;

            Log.d(
                    TAG,
                    "So sánh với " +
                            s.name +
                            ": " +
                            String.format("%.4f", score)
            );

            if (score > bestScore) {

                secondBestScore = bestScore;

                bestScore = score;

                bestMatch = s;

            } else if (score > secondBestScore) {

                secondBestScore = score;
            }
        }

        // =====================================================
        // Không nhận ra
        // =====================================================

        if (bestMatch == null
                || bestScore <
                FaceGeometricHelper.MATCH_THRESHOLD) {

            final float fs = bestScore;

            runOnUiThread(() -> {

                tvStatus.setText(
                        String.format(
                                Locale.getDefault(),
                                "Không nhận ra ai (%.3f < %.3f)",
                                fs,
                                FaceGeometricHelper.MATCH_THRESHOLD
                        )
                );

                Toast.makeText(
                        this,
                        "Khuôn mặt không khớp. Thử đứng thẳng, đủ sáng.",
                        Toast.LENGTH_SHORT
                ).show();

                resetCameraState();
            });

            return;
        }

        // =====================================================
        // Kiểm tra gap
        // =====================================================

        float gap =
                bestScore - secondBestScore;

        if (bestScore < 0.95f
                && gap < FaceGeometricHelper.MIN_GAP) {

            runOnUiThread(() -> {

                tvStatus.setText(
                        "Không đủ tự tin để nhận diện"
                );

                Toast.makeText(
                        this,
                        "Có nhiều khuôn mặt giống nhau. Di chuyển gần camera hơn.",
                        Toast.LENGTH_LONG
                ).show();

                resetCameraState();
            });

            return;
        }

        Student matched = bestMatch;

        float matchScore = bestScore;

        // =====================================================
        // Check đã điểm danh
        // =====================================================

        String today =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                ).format(new Date());

        boolean alreadyChecked = false;

        List<Attendance> todayList =
                db.attendanceDao().getByDate(today);

        for (Attendance existing : todayList) {

            if (existing.studentId == matched.id
                    && existing.classId == currentClassId) {

                alreadyChecked = true;

                break;
            }
        }

        if (alreadyChecked) {

            runOnUiThread(() -> {

                Toast.makeText(
                        this,
                        matched.name +
                                " đã điểm danh rồi!",
                        Toast.LENGTH_SHORT
                ).show();

                tvStatus.setText(
                        "Người này đã điểm danh"
                );

                resetCameraState();
            });

            return;
        }

        // =====================================================
        // Tính trễ
        // =====================================================

        int lateMin = 0;

        if (!classStartTime.isEmpty()) {

            try {

                String[] parts =
                        classStartTime.split(":");

                int startHour =
                        Integer.parseInt(parts[0]);

                int startMinute =
                        Integer.parseInt(parts[1]);

                java.util.Calendar now =
                        java.util.Calendar.getInstance();

                int nowHour =
                        now.get(java.util.Calendar.HOUR_OF_DAY);

                int nowMinute =
                        now.get(java.util.Calendar.MINUTE);

                int nowTotal =
                        nowHour * 60 + nowMinute;

                int startTotal =
                        startHour * 60 + startMinute;

                int diffMin =
                        nowTotal - startTotal;

                if (diffMin > classGraceMinutes) {

                    lateMin = diffMin;
                }

            } catch (Exception ignored) {
            }
        }

        // =====================================================
        // Insert attendance
        // =====================================================

        Attendance a = new Attendance();

        a.studentId = matched.id;
        a.studentName = matched.name;
        a.studentCode = matched.studentCode;
        a.classId = currentClassId;

        a.timestamp = System.currentTimeMillis();

        a.date = today;

        a.time =
                new SimpleDateFormat(
                        "HH:mm",
                        Locale.getDefault()
                ).format(new Date());

        a.lateMinutes = lateMin;

        db.attendanceDao().insert(a);

        // =====================================================
        // Success
        // =====================================================

        final int finalLateMin = lateMin;

        final String classLabel =
                currentClassName != null
                        ? " – " + currentClassName
                        : "";

        runOnUiThread(() -> {

            String lateTag =
                    finalLateMin > 0
                            ? " ⚠️ Trễ " +
                            finalLateMin +
                            " phút"
                            : " ✅ Đúng giờ";

            String msg =
                    String.format(
                            "✅ %s (%s) lúc %s%s%s | %.1f%%",
                            matched.name,
                            matched.studentCode,
                            a.time,
                            classLabel,
                            lateTag,
                            matchScore * 100
                    );

            Toast.makeText(
                    MainActivity.this,
                    msg,
                    Toast.LENGTH_LONG
            ).show();

            tvStatus.setText(
                    "Đã điểm danh: " +
                            matched.name +
                            String.format(
                                    Locale.getDefault(),
                                    " (%.1f%%)",
                                    matchScore * 100
                            )
            );

            resetCameraState();
        });
    }

    // =========================================================
    // Lifecycle camera
    // =========================================================

    @Override
    protected void onPause() {
        super.onPause();

        stopCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();

        previewView.postDelayed(() -> {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED) {

                startCamera();
            }

        }, 300);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {

            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }

            cameraStarted = false;

        } catch (Exception e) {

            Log.e(TAG, "Destroy camera error", e);
        }
    }

    // =========================================================
    // Permission
    // =========================================================

    @Override
    public void onRequestPermissionsResult(
            int code,
            @NonNull String[] perms,
            @NonNull int[] results
    ) {

        super.onRequestPermissionsResult(
                code,
                perms,
                results
        );

        if (code == CAMERA_CODE
                && results.length > 0
                && results[0] ==
                PackageManager.PERMISSION_GRANTED) {

            startCamera();
        }
    }
}