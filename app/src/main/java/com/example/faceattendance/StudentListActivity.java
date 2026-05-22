package com.example.faceattendance;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Student;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StudentListActivity extends AppCompatActivity {

    private RecyclerView rv;
    private TextView tvCount;

    private List<Student> studentList;
    private StudentAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_list);

        rv = findViewById(R.id.rvStudents);
        tvCount = findViewById(R.id.tvCount);

        rv.setLayoutManager(new LinearLayoutManager(this));

        int classId = getIntent().getIntExtra("classId", 0);

        loadStudents(classId);
    }

    private void loadStudents(int classId) {

        new Thread(() -> {

            studentList = classId > 0
                    ? AppDatabase.getInstance(this)
                    .studentDao()
                    .getByClassId(classId)
                    : AppDatabase.getInstance(this)
                    .studentDao()
                    .getAll();

            runOnUiThread(() -> {

                tvCount.setText(studentList.size() + " SV");

                adapter = new StudentAdapter(studentList);

                rv.setAdapter(adapter);
            });

        }).start();
    }

    // ───────────────── Adapter ─────────────────

    class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.VH> {

        private final List<Student> list;

        StudentAdapter(List<Student> list) {
            this.list = list;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup p, int t) {

            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_student, p, false);

            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {

            Student s = list.get(pos);

            String initial = (s.name != null && !s.name.isEmpty())
                    ? String.valueOf(s.name.charAt(0)).toUpperCase()
                    : "?";

            h.tvAvatar.setText(initial);
            h.tvName.setText(s.name);
            h.tvCode.setText(s.studentCode);

            try {

                String d = new SimpleDateFormat(
                        "dd/MM/yyyy",
                        Locale.getDefault()
                ).format(new Date(s.createdAt));

                h.tvDate.setText(d);

            } catch (Exception e) {

                h.tvDate.setText("");
            }

            // ───── NHẤN GIỮ ĐỂ XÓA ─────

            h.itemView.setOnLongClickListener(v -> {

                new AlertDialog.Builder(StudentListActivity.this)
                        .setTitle("Xóa sinh viên")
                        .setMessage("Bạn có chắc muốn xóa " + s.name + "?")
                        .setPositiveButton("Xóa", (dialog, which) -> {

                            new Thread(() -> {

                                try {

                                    // xóa ảnh khuôn mặt
                                    if (s.photoPath != null) {

                                        File file = new File(s.photoPath);

                                        if (file.exists()) {
                                            file.delete();
                                        }
                                    }

                                    // xóa database
                                    AppDatabase.getInstance(StudentListActivity.this)
                                            .studentDao()
                                            .delete(s);

                                    runOnUiThread(() -> {

                                        int position = h.getAdapterPosition();

                                        if (position != RecyclerView.NO_POSITION) {

                                            list.remove(position);

                                            notifyItemRemoved(position);

                                            tvCount.setText(list.size() + " SV");
                                        }

                                        Toast.makeText(
                                                StudentListActivity.this,
                                                "Đã xóa sinh viên",
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    });

                                } catch (Exception e) {

                                    runOnUiThread(() ->
                                            Toast.makeText(
                                                    StudentListActivity.this,
                                                    "Lỗi xóa dữ liệu",
                                                    Toast.LENGTH_SHORT
                                            ).show()
                                    );
                                }

                            }).start();

                        })
                        .setNegativeButton("Hủy", null)
                        .show();

                return true;
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends RecyclerView.ViewHolder {

            TextView tvAvatar, tvName, tvCode, tvDate;

            VH(View v) {
                super(v);

                tvAvatar = v.findViewById(R.id.tvAvatar);
                tvName = v.findViewById(R.id.tvName);
                tvCode = v.findViewById(R.id.tvCode);
                tvDate = v.findViewById(R.id.tvDate);
            }
        }
    }
}