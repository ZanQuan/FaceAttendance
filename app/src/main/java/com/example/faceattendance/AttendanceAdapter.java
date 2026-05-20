package com.example.faceattendance;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.faceattendance.database.Attendance;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.VH> {

    private final List<Attendance> items;

    public AttendanceAdapter(List<Attendance> items) {
        this.items = items;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Attendance a = items.get(pos);

        // Avatar: chữ cái đầu của tên
        String initial = (a.studentName != null && !a.studentName.isEmpty())
                ? String.valueOf(a.studentName.charAt(0)).toUpperCase()
                : "?";
        h.tvAvatar.setText(initial);
        h.tvName.setText(a.studentName);
        h.tvCode.setText(a.studentCode);

        // Format ngày từ yyyy-MM-dd → dd/MM/yyyy
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(a.date);
            h.tvDate.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d));
        } catch (Exception e) {
            h.tvDate.setText(a.date);
        }

        // ── MỚI: hiển thị giờ + trạng thái trễ ──────────────
        if (a.lateMinutes > 0) {
            h.tvTime.setText(a.time + "  ⚠️ Trễ " + a.lateMinutes + " phút");
            h.tvTime.setTextColor(0xFFE53935); // đỏ
        } else {
            h.tvTime.setText(a.time + "  ✅ Đúng giờ");
            h.tvTime.setTextColor(0xFF2E7D32); // xanh lá
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvCode, tvTime, tvDate;
        VH(View v) {
            super(v);
            tvAvatar = v.findViewById(R.id.tvAvatar);
            tvName   = v.findViewById(R.id.tvName);
            tvCode   = v.findViewById(R.id.tvCode);
            tvTime   = v.findViewById(R.id.tvTime);
            tvDate   = v.findViewById(R.id.tvDate);
        }
    }
}
