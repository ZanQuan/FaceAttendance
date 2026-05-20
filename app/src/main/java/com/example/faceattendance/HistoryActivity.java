package com.example.faceattendance;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Attendance;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView    recyclerHistory;
    private LinearLayout    layoutEmpty;
    private TextView        tvTotalCount;
    private MaterialButton  btnClearAll;
    private AttendanceAdapter adapter;

    // ── Nhận từ Intent ──
    private int    classId   = 0;
    private String className = "Tất cả";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Lấy classId và className từ Intent (nếu có)
        classId   = getIntent().getIntExtra("classId", 0);
        String name = getIntent().getStringExtra("className");
        if (name != null) className = name;

        recyclerHistory = findViewById(R.id.recyclerHistory);
        layoutEmpty     = findViewById(R.id.layoutEmpty);
        tvTotalCount    = findViewById(R.id.tvTotalCount);
        btnClearAll     = findViewById(R.id.btnClearAll);

        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        btnClearAll.setOnClickListener(v -> confirmClearAll());

        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            // ── Lọc theo lớp nếu có classId ──
            List<Attendance> list = (classId > 0)
                    ? db.attendanceDao().getByClassId(classId)
                    : db.attendanceDao().getAll();

            // Đếm số người trễ
            int lateCount = 0;
            for (Attendance a : list) {
                if (a.lateMinutes > 0) lateCount++;
            }
            final int finalLate = lateCount;

            runOnUiThread(() -> {
                if (list.isEmpty()) {
                    recyclerHistory.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                    tvTotalCount.setText("0 bản ghi");
                } else {
                    recyclerHistory.setVisibility(View.VISIBLE);
                    layoutEmpty.setVisibility(View.GONE);

                    String countText = list.size() + " bản ghi";
                    if (finalLate > 0)
                        countText += "   ⚠️ " + finalLate + " người trễ";
                    tvTotalCount.setText(countText);

                    adapter = new AttendanceAdapter(list);
                    recyclerHistory.setAdapter(adapter);
                }
            });
        }).start();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa lịch sử?")
                .setMessage(classId > 0
                        ? "Xóa toàn bộ lịch sử lớp \"" + className + "\"?"
                        : "Xóa toàn bộ lịch sử tất cả lớp?")
                .setPositiveButton("Xóa", (d, w) -> {
                    new Thread(() -> {
                        AppDatabase db = AppDatabase.getInstance(this);
                        if (classId > 0)
                            db.attendanceDao().deleteByClassId(classId);
                        else
                            db.attendanceDao().deleteAll();
                        runOnUiThread(this::loadData);
                    }).start();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}