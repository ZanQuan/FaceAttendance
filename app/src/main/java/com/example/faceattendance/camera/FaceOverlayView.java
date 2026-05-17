package com.example.faceattendance.camera;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.google.mlkit.vision.face.Face;
import java.util.List;

public class FaceOverlayView extends View {
    private List<Face> faces;
    private int imageWidth  = 1;
    private int imageHeight = 1;
    private Paint boxPaint;
    private Paint textPaint;

    public FaceOverlayView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);

        textPaint = new Paint();
        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(40f);
        textPaint.setStyle(Paint.Style.FILL);
    }

    public void setFaces(List<Face> faceList,
                         int imgW, int imgH) {
        this.faces       = faceList;
        this.imageWidth  = imgW;
        this.imageHeight = imgH;
        invalidate();
    }

    public void setFaces(List<Face> faceList) {
        setFaces(faceList, imageWidth, imageHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faces == null || faces.isEmpty()) return;

        float scaleX = (float) getWidth()  / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;

        for (int i = 0; i < faces.size(); i++) {
            Face face = faces.get(i);
            RectF box = new RectF(face.getBoundingBox());

            // Scale tọa độ theo kích thước view
            box.left   *= scaleX;
            box.right  *= scaleX;
            box.top    *= scaleY;
            box.bottom *= scaleY;

            canvas.drawRect(box, boxPaint);
            canvas.drawText("Mặt " + (i+1),
                    box.left + 8, box.top - 10, textPaint);
        }
    }
}