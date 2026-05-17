package com.example.faceattendance;

import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Attendance;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import com.example.faceattendance.detector.FaceDetectorHelper;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import com.example.faceattendance.database.Student;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private FaceOverlayView faceOverlay;
    private TextView tvStatus;
    private int faceCount = 0;
    private static final int CAMERA_CODE = 100;

    // Thông tin lớp học được chọn
    private String currentClassCode;
    private String currentClassName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Nhận thông tin lớp từ ClassSelectionActivity
        currentClassCode = getIntent().getStringExtra(ClassSelectionActivity.EXTRA_CLASS_CODE);
        currentClassName = getIntent().getStringExtra(ClassSelectionActivity.EXTRA_CLASS_NAME);

        previewView = findViewById(R.id.previewView);
        faceOverlay = findViewById(R.id.faceOverlay);
        tvStatus    = findViewById(R.id.tvStatus);

        // Hiển thị tên lớp ở status bar
        if (currentClassName != null) {
            tvStatus.setText("Lớp: " + currentClassName + " – Chưa phát hiện khuôn mặt");
        }

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

        btnAttend.setOnClickListener(v -> {
            if (faceCount == 0) {
                Toast.makeText(this, "Không phát hiện khuôn mặt!", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                List<Student> students = db.studentDao().getAll();

                if (students.isEmpty()) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Chưa có SV đăng ký!", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                // TODO: thay bằng face matching thật
                Student matched = students.get(0);

                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                List<Attendance> todayList = db.attendanceDao().getByDate(today);
                for (Attendance existing : todayList) {
                    if (existing.studentId == matched.id) {
                        runOnUiThread(() ->
                                Toast.makeText(this, matched.name + " đã điểm danh hôm nay!", Toast.LENGTH_SHORT).show()
                        );
                        return;
                    }
                }

                Attendance a = new Attendance();
                a.studentId   = matched.id;
                a.studentName = matched.name;
                a.studentCode = matched.studentCode;
                a.timestamp   = System.currentTimeMillis();
                a.date        = today;
                a.time        = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                // Gắn mã lớp vào bản ghi điểm danh (nếu bạn thêm field classCode vào Attendance)
                db.attendanceDao().insert(a);

                runOnUiThread(() ->
                        Toast.makeText(this,
                                "✅ Điểm danh: " + matched.name + " lúc " + a.time
                                        + (currentClassName != null ? " – " + currentClassName : ""),
                                Toast.LENGTH_SHORT).show()
                );
            }).start();
        });

        btnRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        btnStudents.setOnClickListener(v ->
                startActivity(new Intent(this, StudentListActivity.class)));
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(
                        ContextCompat.getMainExecutor(this),
                        new FaceDetectorHelper((faces, w, h) ->
                                runOnUiThread(() -> {
                                    faceCount = faces.size();
                                    faceOverlay.setFaces(faces, w, h);
                                    String classLabel = currentClassName != null
                                            ? "Lớp: " + currentClassName + " – " : "";
                                    tvStatus.setText(faceCount == 0
                                            ? classLabel + "Chưa phát hiện khuôn mặt"
                                            : classLabel + "Phát hiện " + faceCount + " khuôn mặt ✓");
                                })
                        )
                );

                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview, analysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

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