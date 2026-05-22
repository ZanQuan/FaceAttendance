package com.example.faceattendance.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.face.Face;

import java.util.ArrayList;
import java.util.List;

public class FaceOverlayView extends View {

    private final Paint boxPaint = new Paint();

    // danh sách khuôn mặt hiện tại
    private List<Face> faces = new ArrayList<>();

    private int imageWidth = 0;
    private int imageHeight = 0;

    public FaceOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);
    }

    public void setFaces(List<Face> faces, int width, int height) {

        this.faces = faces;
        this.imageWidth = width;
        this.imageHeight = height;

        invalidate();
    }

    // FIX: reset overlay sau mỗi lần điểm danh
    public void clearFaces() {

        faces.clear();

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (faces == null || faces.isEmpty()) {
            return;
        }

        float scaleX = getWidth() / (float) imageWidth;
        float scaleY = getHeight() / (float) imageHeight;

        for (Face face : faces) {

            Rect box = face.getBoundingBox();

            float left   = box.left * scaleX;
            float top    = box.top * scaleY;
            float right  = box.right * scaleX;
            float bottom = box.bottom * scaleY;

            canvas.drawRect(left, top, right, bottom, boxPaint);
        }
    }
}