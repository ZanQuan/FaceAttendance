package com.example.faceattendance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private PreviewView       previewView;
    private TextView          tvCameraStatus;
    private ImageView         imgCaptured;
    private MaterialButton    btnCapture, btnPickGallery, btnRegister, btnViewList;
    private TextInputEditText etName, etCode;
    private ImageCapture      imageCapture;

    private String  savedPhotoPath = null;
    private float[] savedEmbedding = null;
    private int     classId        = 0;

    // ─────────────────────────────────────────────
    // Launcher chọn ảnh từ thư viện
    // ─────────────────────────────────────────────
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) handleGalleryImage(uri);
                    });

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        bindViews();
        classId = getIntent().getIntExtra("classId", 0);

        btnCapture.setOnClickListener(v -> takePhoto());
        btnPickGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnRegister.setOnClickListener(v -> { if (validate()) saveStudent(); });
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

    private void takePhoto() {
        if (imageCapture == null) return;
        tvCameraStatus.setText("Đang chụp...");
        btnCapture.setEnabled(false);

        File dir = new File(getFilesDir(), "faces");
        if (!dir.exists()) dir.mkdirs();
        File photoFile = new File(dir, "sv_" + System.currentTimeMillis() + ".jpg");

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
    // Chọn ảnh từ thư viện
    // ─────────────────────────────────────────────

    private void handleGalleryImage(Uri uri) {
        tvCameraStatus.setText("Đang xử lý ảnh...");
        btnPickGallery.setEnabled(false);

        new Thread(() -> {
            try {
                // Đọc bitmap từ URI
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap original = BitmapFactory.decodeStream(is);
                if (is != null) is.close();

                if (original == null) {
                    runOnUiThread(() -> {
                        tvCameraStatus.setText("Không đọc được ảnh");
                        btnPickGallery.setEnabled(true);
                    });
                    return;
                }

                // Lưu ảnh vào thư mục nội bộ
                File dir = new File(getFilesDir(), "faces");
                if (!dir.exists()) dir.mkdirs();
                File photoFile = new File(dir, "sv_gallery_" + System.currentTimeMillis() + ".jpg");

                FileOutputStream fos = new FileOutputStream(photoFile);
                original.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.close();

                runOnUiThread(() -> {
                    tvCameraStatus.setText("Đang phân tích khuôn mặt...");
                    // Hiển thị preview ảnh đã chọn
                    imgCaptured.setImageBitmap(original);
                    imgCaptured.setVisibility(ImageView.VISIBLE);
                });

                // Xử lý nhận diện khuôn mặt (trên main thread vì ML Kit cần)
                runOnUiThread(() ->
                    tryDetectWithRotation(original, photoFile, new int[]{0, 90, 270, 180}, 0)
                );

            } catch (Exception e) {
                Log.e(TAG, "Gallery image error", e);
                runOnUiThread(() -> {
                    tvCameraStatus.setText("Lỗi: " + e.getMessage());
                    btnPickGallery.setEnabled(true);
                });
            }
        }).start();
    }

    // ─────────────────────────────────────────────
    // Trích xuất embedding từ ảnh
    // ─────────────────────────────────────────────

    private void processPhoto(File photoFile) {
        Bitmap original = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
        if (original == null) {
            tvCameraStatus.setText("Không đọc được ảnh");
            btnCapture.setEnabled(true);
            return;
        }
        tvCameraStatus.setText("Đang phân tích...");
        tryDetectWithRotation(original, photoFile, new int[]{0, 90, 270, 180}, 0);
    }

    private void tryDetectWithRotation(Bitmap original, File photoFile,
                                       int[] angles, int index) {
        if (index >= angles.length) {
            runOnUiThread(() -> {
                tvCameraStatus.setText("Không phát hiện mặt — giữ thẳng & đủ sáng");
                btnCapture.setEnabled(true);
                btnPickGallery.setEnabled(true);
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

        FaceGeometricHelper.extractEmbedding(this, rotated,
                new FaceGeometricHelper.EmbeddingCallback() {
                    @Override
                    public void onSuccess(float[] embedding) {
                        try {
                            FileOutputStream fos = new FileOutputStream(photoFile);
                            finalBmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                            fos.close();
                        } catch (Exception e) {
                            Log.e(TAG, "save error", e);
                        }
                        savedPhotoPath = photoFile.getAbsolutePath();
                        savedEmbedding = embedding;
                        runOnUiThread(() -> {
                            imgCaptured.setImageBitmap(finalBmp);
                            imgCaptured.setVisibility(ImageView.VISIBLE);
                            tvCameraStatus.setText("✓ " + embedding.length
                                    + " điểm đặc trưng (xoay " + angle + "°)");
                            btnCapture.setText("📸 Chụp lại");
                            btnCapture.setEnabled(true);
                            btnPickGallery.setEnabled(true);
                            Toast.makeText(RegisterActivity.this,
                                    "Khuôn mặt OK!", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "OK: " + embedding.length + " features, angle=" + angle);
                        });
                    }

                    @Override
                    public void onFailure(String reason) {
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

        if (name.isEmpty()) { etName.setError("Nhập họ tên"); etName.requestFocus(); return false; }
        if (code.isEmpty()) { etCode.setError("Nhập MSSV");  etCode.requestFocus(); return false; }
        if (savedPhotoPath == null || savedEmbedding == null) {
            Toast.makeText(this, "Vui lòng chụp hoặc chọn ảnh khuôn mặt trước", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void saveStudent() {
        String name = getText(etName);
        String code = getText(etCode);

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            if (db.studentDao().getByCode(code) != null) {
                runOnUiThread(() ->
                        Toast.makeText(this, "MSSV đã tồn tại!", Toast.LENGTH_SHORT).show());
                return;
            }

            Student s = new Student();
            s.name        = name;
            s.studentCode = code;
            s.photoPath   = savedPhotoPath;
            s.createdAt   = System.currentTimeMillis();
            s.classId     = classId;
            s.setFaceEmbedding(savedEmbedding);

            db.studentDao().insert(s);
            Log.d(TAG, "Đã lưu SV: " + name + " | embedding_dim=" + savedEmbedding.length);

            runOnUiThread(() -> {
                Toast.makeText(this, "✅ Đăng ký thành công: " + name, Toast.LENGTH_SHORT).show();
                resetForm();
            });
        }).start();
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private void bindViews() {
        previewView     = findViewById(R.id.previewView);
        tvCameraStatus  = findViewById(R.id.tvCameraStatus);
        imgCaptured     = findViewById(R.id.imgCaptured);
        btnCapture      = findViewById(R.id.btnCapture);
        btnPickGallery  = findViewById(R.id.btnPickGallery);
        btnRegister     = findViewById(R.id.btnRegister);
        btnViewList     = findViewById(R.id.btnViewList);
        etName          = findViewById(R.id.etName);
        etCode          = findViewById(R.id.etCode);
    }

    private void resetForm() {
        etName.setText("");
        etCode.setText("");
        savedPhotoPath = null;
        savedEmbedding = null;
        imgCaptured.setImageBitmap(null);
        imgCaptured.setVisibility(ImageView.GONE);
        tvCameraStatus.setText("Chưa chụp");
        btnCapture.setText("📸 Chụp ảnh");
    }

    private String getText(TextInputEditText et) {
        if (et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Cần quyền camera!", Toast.LENGTH_SHORT).show();
        }
    }
}