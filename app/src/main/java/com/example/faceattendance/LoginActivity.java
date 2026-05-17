package com.example.faceattendance;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Teacher;

public class LoginActivity extends AppCompatActivity {

    public static final String PREF_NAME      = "FaceAttendancePrefs";
    public static final String KEY_TEACHER_ID  = "teacher_id";
    public static final String KEY_TEACHER_NAME = "teacher_name";
    public static final String KEY_LOGGED_IN   = "logged_in";

    private EditText etUsername, etPassword;
    private Button   btnLogin;
    private ProgressBar progressBar;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Nếu đã đăng nhập trước đó → bỏ qua màn hình login
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
            goToClassSelection();
            return;
        }

        setContentView(R.layout.activity_login);

        etUsername  = findViewById(R.id.etUsername);
        etPassword  = findViewById(R.id.etPassword);
        btnLogin    = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        tvError     = findViewById(R.id.tvError);

        // Tạo tài khoản giáo viên mẫu nếu chưa có
        seedDefaultTeacher();

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    // ---------------------------------------------------------------
    // Tạo tài khoản mặc định để demo
    // ---------------------------------------------------------------
    private void seedDefaultTeacher() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            if (db.teacherDao().count() == 0) {
                Teacher demo = new Teacher();
                demo.fullName  = "Nguyễn Văn An";
                demo.username  = "giaovien";
                demo.password  = "123456";
                demo.subject   = "Công nghệ thông tin";
                demo.createdAt = System.currentTimeMillis();
                db.teacherDao().insert(demo);
            }
        }).start();
    }

    // ---------------------------------------------------------------
    // Xử lý đăng nhập
    // ---------------------------------------------------------------
    private void attemptLogin() {
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        tvError.setVisibility(View.GONE);

        if (TextUtils.isEmpty(user)) {
            etUsername.setError("Vui lòng nhập tên đăng nhập");
            etUsername.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(pass)) {
            etPassword.setError("Vui lòng nhập mật khẩu");
            etPassword.requestFocus();
            return;
        }

        btnLogin.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            Teacher teacher = db.teacherDao().login(user, pass);

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);

                if (teacher != null) {
                    // Lưu phiên đăng nhập
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_LOGGED_IN, true)
                            .putInt(KEY_TEACHER_ID, teacher.id)
                            .putString(KEY_TEACHER_NAME, teacher.fullName)
                            .apply();

                    Toast.makeText(this,
                            "Chào mừng, " + teacher.fullName + "!",
                            Toast.LENGTH_SHORT).show();

                    goToClassSelection();
                } else {
                    tvError.setVisibility(View.VISIBLE);
                    tvError.setText("Tên đăng nhập hoặc mật khẩu không đúng");
                }
            });
        }).start();
    }

    private void goToClassSelection() {
        startActivity(new Intent(this, ClassSelectionActivity.class));
        finish(); // Không cho quay lại màn login bằng nút Back
    }
}