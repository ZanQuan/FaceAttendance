package com.example.faceattendance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Student;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private PreviewView previewView;
    private TextView tvCameraStatus;
    private ImageView imgCaptured;

    private MaterialButton btnCapture;
    private MaterialButton btnRegister;
    private MaterialButton btnViewList;

    private TextInputEditText etName;
    private TextInputEditText etCode;

    private ImageCapture imageCapture;

    private String savedPhotoPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        bindViews();

        btnCapture.setOnClickListener(v -> takePhoto());

        btnRegister.setOnClickListener(v -> {
            if (validate()) {
                saveStudent();
            }
        });

        btnViewList.setOnClickListener(v ->
                startActivity(
                        new Intent(this, StudentListActivity.class)
                )
        );

        // Xin quyền camera
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    100
            );

        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == 100
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            startCamera();

        } else {

            Toast.makeText(
                    this,
                    "Cần quyền camera!",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void bindViews() {

        previewView = findViewById(R.id.previewView);

        tvCameraStatus = findViewById(R.id.tvCameraStatus);

        imgCaptured = findViewById(R.id.imgCaptured);

        btnCapture = findViewById(R.id.btnCapture);

        btnRegister = findViewById(R.id.btnRegister);

        btnViewList = findViewById(R.id.btnViewList);

        etName = findViewById(R.id.etName);

        etCode = findViewById(R.id.etCode);
    }

    // ================= CAMERA =================

    private void startCamera() {

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {

            try {

                ProcessCameraProvider provider = future.get();

                Preview preview =
                        new Preview.Builder().build();

                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );

                imageCapture =
                        new ImageCapture.Builder()
                                .setCaptureMode(
                                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                                )
                                .build();

                provider.unbindAll();

                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                );

            } catch (ExecutionException | InterruptedException e) {

                Log.e(TAG, "Camera error", e);
            }

        }, ContextCompat.getMainExecutor(this));
    }

    // ================= TAKE PHOTO =================

    private void takePhoto() {

        if (imageCapture == null) return;

        tvCameraStatus.setText("Đang chụp...");

        btnCapture.setEnabled(false);

        imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),

                new ImageCapture.OnImageCapturedCallback() {

                    @Override
                    @androidx.camera.core.ExperimentalGetImage
                    public void onCaptureSuccess(
                            @NonNull ImageProxy proxy
                    ) {

                        detectFaceAndSave(proxy);
                    }

                    @Override
                    public void onError(
                            @NonNull ImageCaptureException exception
                    ) {

                        tvCameraStatus.setText("Lỗi camera");

                        btnCapture.setEnabled(true);
                    }
                }
        );
    }

    // ================= ML KIT =================

    @androidx.camera.core.ExperimentalGetImage
    private void detectFaceAndSave(ImageProxy proxy) {

        if (proxy.getImage() == null) {

            proxy.close();
            return;
        }

        InputImage image =
                InputImage.fromMediaImage(
                        proxy.getImage(),
                        proxy.getImageInfo().getRotationDegrees()
                );

        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(
                                FaceDetectorOptions.PERFORMANCE_MODE_FAST
                        )
                        .setMinFaceSize(0.2f)
                        .build();

        FaceDetector detector =
                FaceDetection.getClient(options);

        detector.process(image)

                .addOnSuccessListener(faces -> {

                    Bitmap bmp = proxyToBitmap(proxy);

                    proxy.close();

                    if (bmp == null) {

                        tvCameraStatus.setText(
                                "Không đọc được ảnh"
                        );

                        btnCapture.setEnabled(true);

                        return;
                    }

                    if (faces.isEmpty()) {

                        tvCameraStatus.setText(
                                "Không phát hiện khuôn mặt"
                        );

                        btnCapture.setEnabled(true);

                        return;
                    }

                    savedPhotoPath = savePhoto(bmp);

                    imgCaptured.setImageBitmap(bmp);

                    imgCaptured.setVisibility(ImageView.VISIBLE);

                    tvCameraStatus.setText(
                            "✓ Đã nhận diện khuôn mặt"
                    );

                    btnCapture.setText("📸 Chụp lại");

                    btnCapture.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Khuôn mặt OK!",
                            Toast.LENGTH_SHORT
                    ).show();
                })

                .addOnFailureListener(e -> {

                    proxy.close();

                    tvCameraStatus.setText("Lỗi ML Kit");

                    btnCapture.setEnabled(true);
                });
    }

    // ================= BITMAP =================

    private Bitmap proxyToBitmap(ImageProxy proxy) {

        try {

            ByteBuffer buffer =
                    proxy.getPlanes()[0]
                            .getBuffer();

            byte[] bytes =
                    new byte[buffer.remaining()];

            buffer.get(bytes);

            return BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.length
            );

        } catch (Exception e) {

            Log.e(TAG, "Bitmap error", e);

            return null;
        }
    }

    // ================= SAVE PHOTO =================

    private String savePhoto(Bitmap bmp) {

        File dir = new File(
                getFilesDir(),
                "faces"
        );

        if (!dir.exists()) {
            dir.mkdirs();
        }

        String filename =
                "sv_" + System.currentTimeMillis() + ".jpg";

        File file =
                new File(dir, filename);

        try {

            FileOutputStream fos =
                    new FileOutputStream(file);

            bmp.compress(
                    Bitmap.CompressFormat.JPEG,
                    90,
                    fos
            );

            fos.flush();

            fos.close();

        } catch (Exception e) {

            Log.e(TAG, "Save photo error", e);
        }

        return file.getAbsolutePath();
    }

    // ================= VALIDATE =================

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

            Toast.makeText(
                    this,
                    "Vui lòng chụp khuôn mặt",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        return true;
    }

    // ================= SAVE DB =================

    private void saveStudent() {

        String name = getText(etName);

        String code = getText(etCode);

        new Thread(() -> {

            AppDatabase db =
                    AppDatabase.getInstance(this);

            if (db.studentDao()
                    .getByCode(code) != null) {

                runOnUiThread(() ->

                        Toast.makeText(
                                this,
                                "MSSV đã tồn tại!",
                                Toast.LENGTH_SHORT
                        ).show()
                );

                return;
            }

            Student s = new Student();

            s.name = name;

            s.studentCode = code;

            s.photoPath = savedPhotoPath;

            s.createdAt = System.currentTimeMillis();

            db.studentDao().insert(s);

            Log.d(TAG, "Saved: " + name);

            runOnUiThread(() -> {

                Toast.makeText(
                        this,
                        " Đăng ký thành công",
                        Toast.LENGTH_SHORT
                ).show();

                resetForm();
            });

        }).start();
    }

    // ================= RESET =================

    private void resetForm() {

        etName.setText("");

        etCode.setText("");

        savedPhotoPath = null;

        imgCaptured.setImageBitmap(null);

        imgCaptured.setVisibility(ImageView.GONE);

        tvCameraStatus.setText("Chưa chụp");

        btnCapture.setText("Chụp ảnh");
    }

    private String getText(TextInputEditText et) {

        if (et.getText() == null) {
            return "";
        }

        return et.getText()
                .toString()
                .trim();
    }
}