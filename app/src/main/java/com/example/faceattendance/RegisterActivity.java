package com.example.faceattendance;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Student;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private PreviewView previewView;
    private TextView tvCameraStatus;
    private ImageView imgCaptured;
    private MaterialButton btnCapture, btnRegister, btnViewList;
    private TextInputEditText etName, etCode;

    private ImageCapture imageCapture;
    private String savedPhotoPath = null;   // đường dẫn ảnh đã lưu

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        bindViews();
        startCamera();

        btnCapture.setOnClickListener(v -> takePhoto());

        btnRegister.setOnClickListener(v -> {
            if (validate()) saveStudent();
        });

        btnViewList.setOnClickListener(v ->
                startActivity(new Intent(this, StudentListActivity.class))
        );
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }
    }
    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == 100 && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Cần quyền camera!", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Bind ────────────────────────────────────────────────────────
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

    // ── CameraX ─────────────────────────────────────────────────────
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
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview, imageCapture
                );
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ── Chụp ảnh ────────────────────────────────────────────────────
    private void takePhoto() {
        if (imageCapture == null) return;
        tvCameraStatus.setText("Đang chụp…");
        btnCapture.setEnabled(false);

        imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    @androidx.camera.core.ExperimentalGetImage
                    public void onCaptureSuccess(@NonNull ImageProxy proxy) {
                        detectFaceAndSave(proxy);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        tvCameraStatus.setText("Lỗi camera");
                        btnCapture.setEnabled(true);
                    }
                }
        );
    }

    // ── ML Kit nhận diện khuôn mặt ──────────────────────────────────
    @androidx.camera.core.ExperimentalGetImage
    private void detectFaceAndSave(ImageProxy proxy) {
        if (proxy.getImage() == null) { proxy.close(); return; }

        InputImage image = InputImage.fromMediaImage(
                proxy.getImage(),
                proxy.getImageInfo().getRotationDegrees()
        );

        FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.2f)
                .build();

        FaceDetector detector = FaceDetection.getClient(opts);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    // Lấy bitmap từ proxy để lưu file
                    Bitmap bmp = proxyToBitmap(proxy);
                    proxy.close();

                    if (faces.isEmpty()) {
                        tvCameraStatus.setText("Không phát hiện khuôn mặt, thử lại");
                        btnCapture.setEnabled(true);
                        return;
                    }

                    // Lưu ảnh vào bộ nhớ trong
                    savedPhotoPath = savePhoto(bmp);

                    // Hiện ảnh preview nhỏ
                    imgCaptured.setImageBitmap(bmp);
                    imgCaptured.setVisibility(View.VISIBLE);

                    tvCameraStatus.setText("✓ Đã nhận diện " + faces.size() + " khuôn mặt");
                    btnCapture.setText("📸  Chụp lại");
                    btnCapture.setEnabled(true);

                    Toast.makeText(this, "Khuôn mặt OK!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    proxy.close();
                    tvCameraStatus.setText("Lỗi ML Kit");
                    btnCapture.setEnabled(true);
                });
    }

    // ── Bitmap từ ImageProxy ─────────────────────────────────────────
    private Bitmap proxyToBitmap(ImageProxy proxy) {
        ByteBuffer buffer = proxy.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    // ── Lưu ảnh ra file ─────────────────────────────────────────────
    private String savePhoto(Bitmap bmp) {
        File dir = new File(getFilesDir(), "faces");
        if (!dir.exists()) dir.mkdirs();

        String filename = "sv_" + System.currentTimeMillis() + ".jpg";
        File file = new File(dir, filename);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        } catch (Exception e) {
            Log.e(TAG, "Save photo error", e);
        }
        return file.getAbsolutePath();
    }

    // ── Validate ────────────────────────────────────────────────────
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
        if (savedPhotoPath == null) {
            Toast.makeText(this, "Vui lòng chụp ảnh khuôn mặt trước!", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // ── Lưu sinh viên vào DB ────────────────────────────────────────
    private void saveStudent() {
        String name = getText(etName);
        String code = getText(etCode);

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            // Kiểm tra trùng MSSV
            if (db.studentDao().getByCode(code) != null) {
                runOnUiThread(() ->
                        Toast.makeText(this, "MSSV " + code + " đã tồn tại!", Toast.LENGTH_SHORT).show()
                );
                return;
            }

            Student s = new Student();
            s.name        = name;
            s.studentCode = code;
            s.photoPath   = savedPhotoPath;
            s.createdAt   = System.currentTimeMillis();
            db.studentDao().insert(s);

            runOnUiThread(() -> {
                Toast.makeText(this, "✓ Đăng ký thành công: " + name, Toast.LENGTH_SHORT).show();
                resetForm();
            });
        }).start();
    }

    // ── Reset ────────────────────────────────────────────────────────
    private void resetForm() {
        etName.setText("");
        etCode.setText("");
        savedPhotoPath = null;
        imgCaptured.setVisibility(View.GONE);
        tvCameraStatus.setText("Chưa chụp");
        btnCapture.setText("📸  Chụp ảnh khuôn mặt");
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}