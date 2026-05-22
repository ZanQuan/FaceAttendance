package com.example.faceattendance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Student;
import com.example.faceattendance.detector.FaceGeometricHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.List;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private PreviewView       previewView;
    private TextView          tvCameraStatus;
    private ImageView         imgCaptured;
    private MaterialButton    btnCapture, btnRegister, btnViewList;
    private TextInputEditText etName, etCode;
    private ImageCapture      imageCapture;

    private int classId = 0;

    // ── 3 embeddings tương ứng 3 ảnh ──
    private float[] embedding1 = null; // nhìn thẳng
    private float[] embedding2 = null; // nghiêng trái
    private float[] embedding3 = null; // nghiêng phải
    private String  savedPhotoPath = null;

    // Bước chụp hiện tại: 1, 2, 3 — 0 = chưa bắt đầu
    private int captureStep = 0;

    // Hướng dẫn hiển thị cho từng bước
    private static final String[] STEP_GUIDE = {
            "",
            "📸 Ảnh 1/3 — Nhìn THẲNG vào camera",
            "📸 Ảnh 2/3 — Nghiêng nhẹ sang TRÁI",
            "📸 Ảnh 3/3 — Nghiêng nhẹ sang PHẢI",
    };

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        bindViews();

        classId = getIntent().getIntExtra("classId", 0);

        // Bắt đầu từ bước 1 ngay khi vào màn hình
        captureStep = 1;
        tvCameraStatus.setText(STEP_GUIDE[captureStep]);

        btnCapture.setOnClickListener(v -> takePhoto());

        btnRegister.setOnClickListener(v -> {
            if (validate()) saveStudent();
        });

        btnViewList.setOnClickListener(v -> {
            Intent intent = new Intent(this, StudentListActivity.class);
            intent.putExtra("classId", classId);
            startActivity(intent);
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }
    }

    // ─────────────────────────────────────────────
    // Camera
    // ─────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ─────────────────────────────────────────────
    // Chụp ảnh
    // ─────────────────────────────────────────────

    private void takePhoto() {
        if (captureStep == 4) {
            resetForm();
            return;
        }
        if (imageCapture == null) return;
        if (captureStep < 1 || captureStep > 3) return;

        tvCameraStatus.setText("Đang chụp ảnh " + captureStep + "/3...");
        btnCapture.setEnabled(false);

        File dir = new File(getFilesDir(), "faces");
        if (!dir.exists()) dir.mkdirs();
        File photoFile = new File(dir, "sv_" + captureStep + "_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults r) {
                        processPhoto(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        tvCameraStatus.setText("Lỗi camera: " + e.getMessage());
                        btnCapture.setEnabled(true);
                    }
                });
    }

    // ─────────────────────────────────────────────
    // Xử lý ảnh sau khi chụp
    // ─────────────────────────────────────────────

    private void processPhoto(File photoFile) {
        Bitmap original = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
        if (original == null) {
            tvCameraStatus.setText("Không đọc được ảnh");
            btnCapture.setEnabled(true);
            return;
        }

        // Lưu path ảnh đầu tiên làm avatar
        if (captureStep == 1) {
            savedPhotoPath = photoFile.getAbsolutePath();
        }

        tvCameraStatus.setText("Đang phân tích khuôn mặt...");
        tryDetectWithRotation(original, photoFile, new int[]{0, 90, 270, 180}, 0);
    }

    private void tryDetectWithRotation(Bitmap original, File photoFile,
                                       int[] angles, int index) {
        if (index >= angles.length) {
            // Không phát hiện được mặt ở bất kỳ góc nào
            runOnUiThread(() -> {
                tvCameraStatus.setText("Không phát hiện mặt — giữ thẳng & đủ sáng\n"
                        + STEP_GUIDE[captureStep]);
                btnCapture.setEnabled(true);
            });
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

        final Bitmap finalBmp = rotated;
        final int    step     = captureStep; // lưu step hiện tại vào local var

        FaceGeometricHelper.extractEmbedding(this, rotated,
                new FaceGeometricHelper.EmbeddingCallback() {
                    @Override
                    public void onSuccess(float[] embedding) {
                        // Lưu ảnh đã xoay đúng vào file
                        try {
                            FileOutputStream fos = new FileOutputStream(photoFile);
                            finalBmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                            fos.close();
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi lưu ảnh", e);
                        }

                        // Lưu embedding vào đúng slot
                        if (step == 1) embedding1 = embedding;
                        else if (step == 2) embedding2 = embedding;
                        else if (step == 3) embedding3 = embedding;

                        Log.d(TAG, "Ảnh " + step + " OK | features=" + embedding.length
                                + " | angle=" + angle + "°");

                        runOnUiThread(() -> {
                            imgCaptured.setImageBitmap(finalBmp);
                            imgCaptured.setVisibility(ImageView.VISIBLE);

                            if (step < 3) {
                                // Còn bước tiếp theo
                                captureStep = step + 1;
                                String done = "✅ ".repeat(step); // số tick = số ảnh xong
                                tvCameraStatus.setText(done + "\n" + STEP_GUIDE[captureStep]);
                                btnCapture.setText("📸 Chụp ảnh " + captureStep + "/3");
                                btnCapture.setEnabled(true);
                                Toast.makeText(RegisterActivity.this,
                                        "✅ Ảnh " + step + "/3 xong!", Toast.LENGTH_SHORT).show();
                            } else {
                                // Đủ 3 ảnh
                                captureStep = 4; // đánh dấu hoàn thành
                                tvCameraStatus.setText("✅✅✅ Đủ 3 ảnh — Nhập tên & MSSV rồi nhấn Đăng ký");
                                btnCapture.setText("📸 Chụp lại từ đầu");
                                btnCapture.setEnabled(true);
                                Toast.makeText(RegisterActivity.this,
                                        "✅ Đủ 3 ảnh! Nhấn Đăng ký", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(String reason) {
                        // Thử góc xoay tiếp theo
                        tryDetectWithRotation(original, photoFile, angles, index + 1);
                    }
                });
    }

    // ─────────────────────────────────────────────
    // Validate & Save
    // ─────────────────────────────────────────────

    private boolean validate() {
        String name = getText(etName);
        String code = getText(etCode);

        if (name.isEmpty()) {
            etName.setError("Nhập họ tên");
            etName.requestFocus();
            return false;
        }
        if (code.isEmpty()) {
            etCode.setError("Nhập MSSV");
            etCode.requestFocus();
            return false;
        }
        // Bắt buộc ít nhất 2/3 ảnh để đăng ký
        int doneCount = (embedding1 != null ? 1 : 0)
                + (embedding2 != null ? 1 : 0)
                + (embedding3 != null ? 1 : 0);
        if (doneCount < 2) {
            Toast.makeText(this,
                    "Cần chụp ít nhất 2/3 ảnh khuôn mặt (đã có " + doneCount + "/3)",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * Ngưỡng kiểm tra trùng khi đăng ký.
     *
     * Hệ thống dùng Geometric Features (ML Kit Landmark) — KHÔNG phải neural embedding.
     * Với geometric features, hai người khác nhau tự nhiên có similarity 0.95–0.98
     * vì mặt người có tỉ lệ tương đồng. Vì vậy ngưỡng phải cao (0.990–0.995).
     *
     * Cách điều chỉnh:
     *   - Vẫn bị trùng nhầm  → tăng lên 0.995f
     *   - Không bắt được cùng người đăng ký lại → giảm xuống 0.970f
     */
    private static final float DUPLICATE_THRESHOLD = 0.985f;

    private void saveStudent() {
        String name = getText(etName);
        String code = getText(etCode);

        btnRegister.setEnabled(false);

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            // ── 1. Kiểm tra trùng MSSV ──
            if (db.studentDao().getByCodeAndClass(code, classId) != null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "MSSV đã tồn tại!", Toast.LENGTH_SHORT).show();
                    btnRegister.setEnabled(true);
                });
                return;
            }

            // ── 2. Kiểm tra trùng khuôn mặt (dùng điểm TRUNG BÌNH, không phải OR) ──
            //
            // Lý do dùng trung bình thay vì "bất kỳ 1 cặp":
            //   - 3 embedding mới × 3 embedding cũ = tối đa 9 cặp so sánh
            //   - Logic OR (cũ): 1/9 cặp vượt ngưỡng → block → quá nhiều false positive
            //   - Logic AVG (mới): phải có similarity TRUNG BÌNH cao → chắc chắn hơn
            //
            List<Student> allStudents =
                    db.studentDao().getByClassId(classId);

            Student dupStudent = null;

            float bestDupScore = 0f;
            float secondBestScore = 0f;

            float[][] newEmbs = {
                    embedding1,
                    embedding2,
                    embedding3
            };

            for (Student existing : allStudents) {

                float[][] storedEmbs = {
                        existing.getFaceEmbedding(),
                        existing.getFaceEmbedding2(),
                        existing.getFaceEmbedding3()
                };

                float totalScore = 0f;
                int pairCount = 0;

                float maxScore = 0f;
                int strongMatchCount = 0;

                for (float[] newEmb : newEmbs) {

                    if (newEmb == null) continue;

                    for (float[] storedEmb : storedEmbs) {

                        if (storedEmb == null) continue;

                        float score =
                                FaceGeometricHelper.cosineSimilarity(
                                        newEmb,
                                        storedEmb
                                );

                        totalScore += score;
                        pairCount++;

                        if (score > maxScore) {
                            maxScore = score;
                        }

                        // chỉ tính strong nếu cực giống
                        if (score >= 0.995f) {
                            strongMatchCount++;
                        }
                    }
                }

                if (pairCount == 0) continue;

                float avgScore = totalScore / pairCount;

                Log.d(TAG,
                        "DupCheck vs " + existing.name +
                                " | avg=" + String.format("%.4f", avgScore) +
                                " | max=" + String.format("%.4f", maxScore) +
                                " | strong=" + strongMatchCount);

                // lưu top1 và top2
                if (avgScore > bestDupScore) {

                    secondBestScore = bestDupScore;

                    bestDupScore = avgScore;
                }
                else if (avgScore > secondBestScore) {

                    secondBestScore = avgScore;
                }

                /*
                 * Điều kiện duplicate MỚI
                 *
                 * 1. average phải rất cao
                 * 2. max phải cực cao
                 * 3. có ít nhất 2 cặp cực giống
                 * 4. phải vượt người thứ 2 đủ xa
                 */

                float gap = avgScore - secondBestScore;

                boolean isDuplicate =

                        avgScore >= 0.985f

                                && maxScore >= 0.995f

                                && strongMatchCount >= 2

                                && gap >= 0.003f;

                if (isDuplicate) {

                    dupStudent = existing;

                    bestDupScore = avgScore;

                    break;
                }
            }

            if (dupStudent != null) {

                final Student dup = dupStudent;
                final float score = bestDupScore;

                runOnUiThread(() -> {

                    Toast.makeText(
                            this,
                            String.format(
                                    "❌ Khuôn mặt đã được đăng ký!\n\nTrùng với:\n%s (%s)\n\nĐộ giống: %.2f%%",
                                    dup.name,
                                    dup.studentCode,
                                    score * 100
                            ),
                            Toast.LENGTH_LONG
                    ).show();

                    btnRegister.setEnabled(true);
                });

                return;
            }



            // ── 3. Lưu sinh viên mới ──
            Student s = new Student();
            s.name        = name;
            s.studentCode = code;
            s.photoPath   = savedPhotoPath;
            s.createdAt   = System.currentTimeMillis();
            s.classId     = classId;

            // Lưu cả 3 embedding
            s.setFaceEmbedding(embedding1);
            s.setFaceEmbedding2(embedding2);
            s.setFaceEmbedding3(embedding3);

            db.studentDao().insert(s);

            int dim = embedding1 != null ? embedding1.length : 0;
            Log.d(TAG, "Đã lưu: " + name + " | embeddings=3 | dim=" + dim);

            runOnUiThread(() -> {
                Toast.makeText(this,
                        "✅ Đăng ký thành công: " + name, Toast.LENGTH_LONG).show();
                resetForm();
                btnRegister.setEnabled(true);
            });
        }).start();
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private void bindViews() {
        previewView    = findViewById(R.id.previewView);
        tvCameraStatus = findViewById(R.id.tvCameraStatus);
        imgCaptured    = findViewById(R.id.imgCaptured);
        btnCapture     = findViewById(R.id.btnCapture);
        btnRegister    = findViewById(R.id.btnRegister);
        btnViewList    = findViewById(R.id.btnViewList);
        etName         = findViewById(R.id.etName);
        etCode         = findViewById(R.id.etCode);
    }

    private void resetForm() {
        etName.setText("");
        etCode.setText("");

        // Reset embedding và bước chụp
        embedding1 = embedding2 = embedding3 = null;
        savedPhotoPath = null;
        captureStep = 1;

        imgCaptured.setImageBitmap(null);
        imgCaptured.setVisibility(ImageView.GONE);
        tvCameraStatus.setText(STEP_GUIDE[captureStep]);
        btnCapture.setText("📸 Chụp ảnh 1/3");
    }

    private String getText(TextInputEditText et) {
        if (et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Cần quyền camera!", Toast.LENGTH_SHORT).show();
        }
    }
}