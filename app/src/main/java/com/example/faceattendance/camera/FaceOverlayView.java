package com.example.faceattendance.camera;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.google.mlkit.vision.face.Face;
import java.util.List;

public class FaceOverlayView extends View {
    private List<Face> faces;
    private Paint paint;

    public FaceOverlayView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
    }

    public void setFaces(List<Face> faceList) {
        this.faces = faceList;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faces == null) return;
        for (Face face : faces) {
            RectF box = new RectF(face.getBoundingBox());
            canvas.drawRect(box, paint);
        }
    }
}