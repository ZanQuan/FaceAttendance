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

    private RecyclerView recyclerHistory;
    private LinearLayout layoutEmpty;
    private TextView tvTotalCount;
    private MaterialButton btnClearAll;

    private AttendanceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

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
            List<Attendance> list = AppDatabase.getInstance(this)
                    .attendanceDao().getAll();

            runOnUiThread(() -> {
                if (list.isEmpty()) {
                    recyclerHistory.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                    tvTotalCount.setText("0 bản ghi");
                } else {
                    recyclerHistory.setVisibility(View.VISIBLE);
                    layoutEmpty.setVisibility(View.GONE);
                    tvTotalCount.setText(list.size() + " bản ghi");

                    adapter = new AttendanceAdapter(list);
                    recyclerHistory.setAdapter(adapter);
                }
            });
        }).start();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa tất cả lịch sử?")
                .setMessage("Thao tác này không thể hoàn tác.")
                .setPositiveButton("Xóa", (d, w) -> {
                    new Thread(() -> {
                        AppDatabase.getInstance(this).attendanceDao().deleteAll();
                        runOnUiThread(this::loadData);
                    }).start();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}


// ══════════════════════════════════════════════════════════════
//  AttendanceAdapter.java
// ══════════════════════════════════════════════════════════════
