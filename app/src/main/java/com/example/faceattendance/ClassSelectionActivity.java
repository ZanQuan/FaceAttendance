package com.example.faceattendance;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.ClassRoomEntity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ClassSelectionActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_CODE = "class_code";
    public static final String EXTRA_CLASS_NAME = "class_name";

    private List<ClassRoomEntity> classList = new ArrayList<>();
    private ClassAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_selection);

        // Hiển thị tên giáo viên
        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE);
        String teacherName = prefs.getString(LoginActivity.KEY_TEACHER_NAME, "Giáo viên");
        TextView tvTeacherName = findViewById(R.id.tvTeacherName);
        tvTeacherName.setText("Xin chào, " + teacherName);

        // Nút đăng xuất
        findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());

        // RecyclerView
        RecyclerView rv = findViewById(R.id.rvClasses);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ClassAdapter(classList);
        rv.setAdapter(adapter);

        // FAB thêm lớp
        FloatingActionButton fab = findViewById(R.id.fabAddClass);
        fab.setOnClickListener(v -> showAddClassDialog());

        // Load danh sách lớp từ DB
        loadClasses();
    }

    // ---------------------------------------------------------------
    // Load từ Room DB
    // ---------------------------------------------------------------
    private void loadClasses() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<ClassRoomEntity> data = db.classRoomDao().getAll();
            runOnUiThread(() -> {
                classList.clear();
                classList.addAll(data);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    // ---------------------------------------------------------------
    // Dialog thêm lớp học
    // ---------------------------------------------------------------
    private void showAddClassDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_class, null);

        EditText etName  = dialogView.findViewById(R.id.etClassName);
        EditText etCode  = dialogView.findViewById(R.id.etClassCode);
        EditText etRoom  = dialogView.findViewById(R.id.etClassRoom);
        EditText etTime  = dialogView.findViewById(R.id.etClassTime);
        EditText etTotal = dialogView.findViewById(R.id.etClassTotal);

        new AlertDialog.Builder(this)
                .setTitle("➕  Thêm lớp học")
                .setView(dialogView)
                .setPositiveButton("Thêm", (dialog, which) -> {
                    String name  = etName.getText().toString().trim();
                    String code  = etCode.getText().toString().trim();
                    String room  = etRoom.getText().toString().trim();
                    String time  = etTime.getText().toString().trim();
                    String total = etTotal.getText().toString().trim();

                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(code) || TextUtils.isEmpty(room)) {
                        Toast.makeText(this, "Vui lòng nhập đủ tên lớp, mã lớp và phòng học!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ClassRoomEntity cls = new ClassRoomEntity();
                    cls.name      = name;
                    cls.code      = code;
                    cls.room      = room;
                    cls.time      = time.isEmpty() ? "—" : time;
                    cls.total     = total.isEmpty() ? 0 : Integer.parseInt(total);
                    cls.createdAt = System.currentTimeMillis();

                    new Thread(() -> {
                        AppDatabase.getInstance(this).classRoomDao().insert(cls);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Đã thêm lớp " + name, Toast.LENGTH_SHORT).show();
                            loadClasses(); // Reload lại danh sách
                        });
                    }).start();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ---------------------------------------------------------------
    // Xác nhận đăng xuất
    // ---------------------------------------------------------------
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất không?")
                .setPositiveButton("Đăng xuất", (d, w) -> {
                    getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE)
                            .edit().clear().apply();
                    startActivity(new Intent(this, LoginActivity.class));
                    finishAffinity();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ---------------------------------------------------------------
    // RecyclerView Adapter
    // ---------------------------------------------------------------
    private class ClassAdapter extends RecyclerView.Adapter<ClassAdapter.VH> {

        private final List<ClassRoomEntity> data;

        ClassAdapter(List<ClassRoomEntity> data) { this.data = data; }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_class_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            ClassRoomEntity cls = data.get(pos);
            h.tvCode.setText(cls.code);
            h.tvName.setText(cls.name);
            h.tvRoom.setText("🏫 " + cls.room);
            h.tvTime.setText("🕐 " + cls.time);
            h.tvTotal.setText("👥 " + cls.total + " sinh viên");

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ClassSelectionActivity.this, MainActivity.class);
                intent.putExtra(EXTRA_CLASS_CODE, cls.code);
                intent.putExtra(EXTRA_CLASS_NAME, cls.name);
                intent.putExtra("classId", cls.id);
                startActivity(intent);
            });

            // Giữ để xóa lớp
            h.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(ClassSelectionActivity.this)
                        .setTitle("Xóa lớp")
                        .setMessage("Xóa lớp \"" + cls.name + "\"?")
                        .setPositiveButton("Xóa", (d, w) -> {
                            new Thread(() -> {
                                AppDatabase db = AppDatabase.getInstance(ClassSelectionActivity.this);

                                // Xóa sinh viên thuộc lớp này
                                db.studentDao().deleteByClassId(cls.id);

                                // Xóa lịch sử điểm danh của lớp
                                db.attendanceDao().deleteByClassId(cls.id);

                                // Xóa lớp
                                db.classRoomDao().delete(cls);

                                runOnUiThread(() -> {
                                    // reload danh sách
                                    loadClasses();
                                    Toast.makeText(ClassSelectionActivity.this, "Đã xóa lớp và dữ liệu liên quan", Toast.LENGTH_SHORT).show();
                                });
                            }).start();
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCode, tvName, tvRoom, tvTime, tvTotal;
            VH(View v) {
                super(v);
                tvCode  = v.findViewById(R.id.tvClassCode);
                tvName  = v.findViewById(R.id.tvClassName);
                tvRoom  = v.findViewById(R.id.tvClassRoom);
                tvTime  = v.findViewById(R.id.tvClassTime);
                tvTotal = v.findViewById(R.id.tvClassTotal);
            }
        }
    }
}