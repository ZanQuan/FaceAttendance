package com.example.faceattendance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.faceattendance.database.AppDatabase;
import com.example.faceattendance.database.Attendance;
import com.example.faceattendance.database.ClassRoomEntity;
import com.google.android.material.button.MaterialButton;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private Spinner spinnerClass;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private List<ClassRoomEntity> allClasses = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        spinnerClass  = findViewById(R.id.spinnerClass);
        progressBar   = findViewById(R.id.progressBar);
        tvStatus      = findViewById(R.id.tvStatus);
        MaterialButton btnAttendance = findViewById(R.id.btnExportAttendance);
        MaterialButton btnLate       = findViewById(R.id.btnExportLate);

        loadClasses();

        btnAttendance.setOnClickListener(v -> exportExcel(false));
        btnLate.setOnClickListener(v       -> exportExcel(true));
    }

    // ──────────────────────────────────────────────
    // Load danh sách lớp vào Spinner
    // ──────────────────────────────────────────────
    private void loadClasses() {
        new Thread(() -> {
            allClasses = AppDatabase.getInstance(this).classRoomDao().getAll();
            runOnUiThread(() -> {
                List<String> names = new ArrayList<>();
                names.add("Tất cả lớp");
                for (ClassRoomEntity c : allClasses) {
                    names.add(c.name + " (" + c.code + ")");
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        this, android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerClass.setAdapter(adapter);
            });
        }).start();
    }

    // ──────────────────────────────────────────────
    // Xuất Excel
    // ──────────────────────────────────────────────
    private void exportExcel(boolean lateOnly) {
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);

                // Xác định lớp đã chọn
                int selectedIndex = spinnerClass.getSelectedItemPosition();
                int filterClassId = -1; // -1 = tất cả
                if (selectedIndex > 0) {
                    filterClassId = allClasses.get(selectedIndex - 1).id;
                }

                // Lấy dữ liệu
                List<Attendance> data;
                if (lateOnly) {
                    data = (filterClassId == -1)
                            ? db.attendanceDao().getLateStudents()
                            : db.attendanceDao().getLateStudentsByClass(filterClassId);
                } else {
                    data = (filterClassId == -1)
                            ? db.attendanceDao().getAll()
                            : db.attendanceDao().getByClassId(filterClassId);
                }

                // Tạo file Excel
                File excelFile = buildExcel(data, lateOnly);

                // Mở file để xem / chia sẻ
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("✅ Đã lưu: " + excelFile.getName());
                    openFile(excelFile);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ──────────────────────────────────────────────
    // Tạo file Excel với Apache POI
    // ──────────────────────────────────────────────
    private File buildExcel(List<Attendance> data, boolean lateOnly) throws Exception {

        Workbook workbook = new XSSFWorkbook();
        String sheetName  = lateOnly ? "Danh sách đi trễ" : "Danh sách điểm danh";
        Sheet sheet       = workbook.createSheet(sheetName);

        // ── Style tiêu đề ──
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        // ── Style dữ liệu ──
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);

        // ── Style đi trễ (màu đỏ nhạt) ──
        CellStyle lateStyle = workbook.createCellStyle();
        lateStyle.cloneStyleFrom(dataStyle);
        lateStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        lateStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // ── Tiêu đề cột ──
        Row header = sheet.createRow(0);
        String[] columns = lateOnly
                ? new String[]{"STT", "Mã SV", "Họ tên", "Ngày", "Giờ điểm danh", "Phút trễ", "Lớp (ID)"}
                : new String[]{"STT", "Mã SV", "Họ tên", "Ngày", "Giờ điểm danh", "Trạng thái", "Lớp (ID)"};

        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        // ── Dữ liệu ──
        for (int i = 0; i < data.size(); i++) {
            Attendance a = data.get(i);
            Row row = sheet.createRow(i + 1);

            CellStyle rowStyle = (a.lateMinutes > 0) ? lateStyle : dataStyle;

            createCell(row, 0, String.valueOf(i + 1),          rowStyle);
            createCell(row, 1, a.studentCode != null ? a.studentCode : "", rowStyle);
            createCell(row, 2, a.studentName != null ? a.studentName : "", rowStyle);
            createCell(row, 3, a.date  != null ? a.date  : "",             rowStyle);
            createCell(row, 4, a.time  != null ? a.time  : "",             rowStyle);

            if (lateOnly) {
                createCell(row, 5, a.lateMinutes + " phút", rowStyle);
            } else {
                String status = (a.lateMinutes > 0)
                        ? "Trễ " + a.lateMinutes + " phút"
                        : "Đúng giờ";
                createCell(row, 5, status, rowStyle);
            }
            createCell(row, 6, String.valueOf(a.classId), rowStyle);
        }

        // Đặt độ rộng cố định cho từng cột (đơn vị: 1/256 ký tự)
        sheet.setColumnWidth(0, 2000);   // STT
        sheet.setColumnWidth(1, 4000);   // Mã SV
        sheet.setColumnWidth(2, 8000);   // Họ tên
        sheet.setColumnWidth(3, 4000);   // Ngày
        sheet.setColumnWidth(4, 4000);   // Giờ
        sheet.setColumnWidth(5, 5000);   // Trạng thái / Phút trễ
        sheet.setColumnWidth(6, 3000);   // Lớp ID

        // ── Lưu file ──
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        String fileName   = (lateOnly ? "BaoCao_DiTre_" : "BaoCao_DiemDanh_") + timestamp + ".xlsx";

        File dir = new File(getCacheDir(), "excel");
        if (!dir.exists()) dir.mkdirs();
        File outFile = new File(dir, fileName);

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            workbook.write(fos);
        }
        workbook.close();
        return outFile;
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    // ──────────────────────────────────────────────
    // Mở file / chia sẻ
    // ──────────────────────────────────────────────
    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Mở file Excel bằng..."));
        } catch (Exception e) {
            Toast.makeText(this, "Không tìm thấy app mở Excel. File đã lưu thành công.", Toast.LENGTH_LONG).show();
        }
    }
}