package com.example.faceattendance;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Student;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StudentListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_list);

        RecyclerView rv  = findViewById(R.id.rvStudents);
        TextView tvCount = findViewById(R.id.tvCount);

        rv.setLayoutManager(new LinearLayoutManager(this));

        new Thread(() -> {
            List<Student> list = AppDatabase.getInstance(this)
                    .studentDao().getAll();
            runOnUiThread(() -> {
                tvCount.setText(list.size() + " SV");
                rv.setAdapter(new StudentAdapter(list));
            });
        }).start();
    }

    // ── Adapter nội bộ ───────────────────────────────────────────
    static class StudentAdapter
            extends RecyclerView.Adapter<StudentAdapter.VH> {

        private final List<Student> list;
        StudentAdapter(List<Student> list) { this.list = list; }

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
                String d = new SimpleDateFormat("dd/MM/yyyy",
                        Locale.getDefault())
                        .format(new Date(s.createdAt));
                h.tvDate.setText(d);
            } catch (Exception e) {
                h.tvDate.setText("");
            }
        }
        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvAvatar, tvName, tvCode, tvDate;
            VH(View v) {
                super(v);
                tvAvatar = v.findViewById(R.id.tvAvatar);
                tvName   = v.findViewById(R.id.tvName);
                tvCode   = v.findViewById(R.id.tvCode);
                tvDate   = v.findViewById(R.id.tvDate);
            }
        }
    }
}