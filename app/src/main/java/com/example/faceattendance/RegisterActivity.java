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

import android.graphics.Matrix;
import android.net.Uri;




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
                        CameraSelector.DEFAULT_FRONT_CAMERA,
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

        // Tạo file output
        File dir = new File(getFilesDir(), "faces");
        if (!dir.exists()) dir.mkdirs();
        File photoFile = new File(dir, "sv_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // Lưu thẳng ra file JPEG — tránh hoàn toàn vấn đề YUV buffer
        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        detectFaceFromFile(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        tvCameraStatus.setText("Lỗi camera: " + e.getMessage());
                        btnCapture.setEnabled(true);
                    }
                }
        );
    }

    // ================= ML KIT TỪ FILE =================
    private void detectFaceFromFile(File photoFile) {
        // Load bitmap từ file JPEG — luôn đúng, không phụ thuộc format emulator
        Bitmap bmp = BitmapFactory.decodeFile(photoFile.getAbsolutePath());

        if (bmp == null) {
            tvCameraStatus.setText("Không đọc được ảnh");
            btnCapture.setEnabled(true);
            return;
        }

        // Tạo InputImage từ file
        InputImage image;
        try {
            image = InputImage.fromFilePath(this,
                    android.net.Uri.fromFile(photoFile));
        } catch (Exception e) {
            tvCameraStatus.setText("Lỗi xử lý ảnh");
            btnCapture.setEnabled(true);
            return;
        }

        FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.2f)
                .build();

        FaceDetection.getClient(opts).process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        tvCameraStatus.setText("Không phát hiện khuôn mặt, thử lại");
                        photoFile.delete(); // xóa ảnh không hợp lệ
                        btnCapture.setEnabled(true);
                        return;
                    }

                    // Thành công
                    savedPhotoPath = photoFile.getAbsolutePath();
                    imgCaptured.setImageBitmap(bmp);
                    imgCaptured.setVisibility(ImageView.VISIBLE);
                    tvCameraStatus.setText("✓ Đã nhận diện khuôn mặt");
                    btnCapture.setText("📸 Chụp lại");
                    btnCapture.setEnabled(true);
                    Toast.makeText(this, "Khuôn mặt OK!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    tvCameraStatus.setText("Lỗi ML Kit: " + e.getMessage());
                    btnCapture.setEnabled(true);
                });
    }

    // ================= ML KIT =================

    @androidx.camera.core.ExperimentalGetImage
    private void detectFaceAndSave(ImageProxy proxy) {
        if (proxy.getImage() == null) { proxy.close(); return; }

        // ✅ Lấy bitmap TRƯỚC — trước khi ML Kit đọc buffer
        Bitmap bmp = proxyToBitmap(proxy);

        InputImage image = InputImage.fromMediaImage(
                proxy.getImage(),
                proxy.getImageInfo().getRotationDegrees()
        );

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.2f)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    proxy.close(); // đóng sau cùng

                    if (bmp == null) {
                        tvCameraStatus.setText("Không đọc được ảnh");
                        btnCapture.setEnabled(true);
                        return;
                    }
                    if (faces.isEmpty()) {
                        tvCameraStatus.setText("Không phát hiện khuôn mặt, thử lại");
                        btnCapture.setEnabled(true);
                        return;
                    }

                    savedPhotoPath = savePhoto(bmp);
                    imgCaptured.setImageBitmap(bmp);
                    imgCaptured.setVisibility(ImageView.VISIBLE);
                    tvCameraStatus.setText("✓ Đã nhận diện khuôn mặt");
                    btnCapture.setText("📸 Chụp lại");
                    btnCapture.setEnabled(true);
                    Toast.makeText(this, "Khuôn mặt OK!", Toast.LENGTH_SHORT).show();
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
            ImageProxy.PlaneProxy[] planes = proxy.getPlanes();
            int width  = proxy.getWidth();
            int height = proxy.getHeight();

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int yRowStride    = planes[0].getRowStride();
            int uvRowStride   = planes[1].getRowStride();
            int uvPixelStride = planes[1].getPixelStride();

            // Convert YUV_420_888 → ARGB (đúng với mọi stride, mọi thiết bị/emulator)
            int[] argb = new int[width * height];
            int idx = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int yVal = yBuffer.get(y * yRowStride + x) & 0xFF;

                    int uvRow = (y / 2) * uvRowStride;
                    int uvCol = (x / 2) * uvPixelStride;
                    int uVal  = uBuffer.get(uvRow + uvCol) & 0xFF;
                    int vVal  = vBuffer.get(uvRow + uvCol) & 0xFF;

                    int r = (int)(yVal + 1.402f   * (vVal - 128));
                    int g = (int)(yVal - 0.344136f * (uVal - 128) - 0.714136f * (vVal - 128));
                    int b = (int)(yVal + 1.772f    * (uVal - 128));

                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    argb[idx++] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }

            Bitmap raw = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888);

            // Xoay + flip cho camera trước
            Matrix matrix = new Matrix();
            matrix.postRotate(proxy.getImageInfo().getRotationDegrees());
            matrix.postScale(-1f, 1f, raw.getWidth() / 2f, raw.getHeight() / 2f);

            return Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), matrix, true);

        } catch (Exception e) {
            Log.e(TAG, "Bitmap error: " + e.getMessage(), e);
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